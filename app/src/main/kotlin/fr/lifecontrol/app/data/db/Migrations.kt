package fr.lifecontrol.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2 : le modèle « Grignotage » passe d'un suivi oui/non à un nombre de craquages par jour
 * (comme la cigarette : objectif zéro). Les habitudes déjà créées depuis ce modèle sont converties,
 * historique compris : « tenu » → 0, « craqué » → 1 (le nombre réel n'était pas connu).
 * Le schéma ne change pas.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        val snacking = "SELECT id FROM habit WHERE template_id = 'snacking' AND tracking_mode = 'BINARY' AND direction = 'QUIT'"
        db.execSQL(
            """
            UPDATE entry SET
                quantity = CASE binary_value WHEN 1 THEN 0 ELSE 1 END,
                binary_value = NULL,
                updated_at = ?
            WHERE habit_id IN ($snacking) AND binary_value IS NOT NULL AND quantity IS NULL
            """.trimIndent(),
            arrayOf<Any>(now),
        )
        db.execSQL(
            """
            UPDATE habit SET tracking_mode = 'QUANTITY', unit = 'grignotages', step = 1, updated_at = ?
            WHERE id IN ($snacking)
            """.trimIndent(),
            arrayOf<Any>(now),
        )
    }
}

/** v2 → v4 directement : seule la table des cartes du jour est créée. */
val MIGRATION_2_4 = object : Migration(2, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(CARD_TABLE_SQL)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_day` ON `card` (`day`)")
    }
}

/**
 * v3 → v4 : les cartes ne sont plus embarquées mais générées en direct (une par jour).
 * Les données de l'ancien paquet prégénéré (cartes obtenues, tirages) sont supprimées.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `card_unlock`")
        db.execSQL("DROP TABLE IF EXISTS `card_roll`")
        db.execSQL(CARD_TABLE_SQL)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_day` ON `card` (`day`)")
    }
}

internal const val CARD_TABLE_SQL = "CREATE TABLE IF NOT EXISTS `card` (`id` TEXT NOT NULL, `day` TEXT NOT NULL, " +
    "`source` TEXT NOT NULL, `rarity` TEXT NOT NULL, `success_rate` REAL NOT NULL, `status` TEXT NOT NULL, " +
    "`created_at` INTEGER NOT NULL, `qid` TEXT, `title` TEXT, `theme_id` TEXT, `theme_label` TEXT, `summary` TEXT, " +
    "`text` TEXT, `url` TEXT, `views` INTEGER, `image_url` TEXT, `image_artist` TEXT, `image_license` TEXT, " +
    "`image_license_url` TEXT, `image_page` TEXT, PRIMARY KEY(`id`))"

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_4, MIGRATION_3_4)
