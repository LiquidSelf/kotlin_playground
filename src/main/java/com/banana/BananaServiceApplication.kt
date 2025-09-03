package com.banana

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
open class BananaServiceApplication

fun main(args: Array<String>) {
    runApplication<BananaServiceApplication>(*args)
}