package com.github.evp2.persistence

import org.jetbrains.exposed.sql.Table

object ProductTable : Table() {

    val upc = integer("upc").autoIncrement()
    val name = text("name")
    val description = text("description")
    val price = double("price")

    override val primaryKey: PrimaryKey = PrimaryKey(upc)
}
