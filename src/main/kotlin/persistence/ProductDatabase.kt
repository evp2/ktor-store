package com.github.evp2.persistence

import com.github.evp2.model.Product
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object ProductDatabase {

    init {
        val driverClassName = "org.h2.Driver"
        val jdbcURL = "jdbc:h2:file:./build/db?DB_CLOSE_DELAY=-1"
        val database = Database.connect(jdbcURL, driverClassName)

        transaction {
            SchemaUtils.create(ProductTable) // create table if it doesn't exist
        }
    }

    val dao = ProductDAO().apply {
        runBlocking {
            if (products().isEmpty()) {
                addProducts(
                    listOf(
                        Product(1, "Coffee Mug", "Your new favorite mug", 15.00),
                        Product(2, "T-shirt", "Your new favorite shirt", 25.00),
                        Product(3, "Backpack", "Your new favorite backpack", 85.00)
                    )
                )
            }
        }
    }
}