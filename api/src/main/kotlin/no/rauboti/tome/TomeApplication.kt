package no.rauboti.tome

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TomeApplication

fun main(args: Array<String>) {
    runApplication<TomeApplication>(*args)
}
