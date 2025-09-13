package com.johang.audiocinemateca.data.local.migrations

import androidx.room.DeleteColumn
import androidx.room.AutoMigration
import androidx.room.migration.AutoMigrationSpec

@DeleteColumn(
    tableName = "search_history",
    columnName = "id"
)
@AutoMigration(from = 4, to = 5)
class Migration4To5 : AutoMigrationSpec