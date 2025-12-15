package com.github.evp2.persistence

import com.github.evp2.model.Product
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ProductDAO {

    suspend fun product(upc: Int): Product? {
        return query {
            ProductTable
                .select { ProductTable.upc.eq(upc) }
                .firstOrNull()?.toProduct()
        }
    }

    suspend fun products(): List<Product> {
        return query {
            ProductTable.selectAll().map { resultRow ->
                resultRow.toProduct()
            }
        }
    }

    suspend fun addProducts(products: List<Product>): List<Product>? {
        return query {
            products.forEach { product ->
                addProduct(product)
            }
            products
        }
    }

    suspend fun addProduct(product: Product): Product? {
        return query {
            val insertedProduct = ProductTable.insert { table ->
                table[ProductTable.upc] = product.upc
                table[ProductTable.name] = product.name
                table[ProductTable.description] = product.description
                table[ProductTable.price] = product.price
            }

            insertedProduct.resultedValues?.singleOrNull()?.toProduct()
        }
    }

    private fun ResultRow.toProduct(): Product {
        return Product(
            upc = this[ProductTable.upc],
            name = this[ProductTable.name],
            description = this[ProductTable.description],
            price = this[ProductTable.price]
        )
    }

    private suspend fun <T> query(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}