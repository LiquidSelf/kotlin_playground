package com.banana.playground

import reactor.core.publisher.Flux

fun main() {
    Flux.concat(f1(), f6(), f11())
        .buffer(5)
        .subscribe { println(it) }
}

fun f1(): Flux<Int> {
    return Flux.range(1, 5)
}

fun f6(): Flux<Int> {
    return Flux.range(6, 5)
}

fun f11(): Flux<Int> {
    return Flux.range(11, 5)
}