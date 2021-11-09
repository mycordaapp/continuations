# The "Continuations" framework

[![Circle CI](https://circleci.com/gh/mycordaapp/continuations.svg?style=shield)](https://circleci.com/gh/mycordaapp/continuations)
[![Licence Status](https://img.shields.io/github/license/mycordaapp/continuations)](https://github.com/mycordaapp/continuations/blob/master/licence.txt)

## What it does

A vaguely [continuations](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.coroutines/-continuation/) style concept
but designed to operate at cloud scale. So we resume a process, somewhere on 
the cloud.

This is written to support [Tasks](https://github.com/mycordaapp/tasks) however the concept is sufficiently simple that
and standalone that it can have its own repo.

## Design 



## Dependencies

As with everything in [myCorda dot App](https://mycorda.app), this library has minimal dependencies:

* Kotlin 1.4
* Java 11
* The object [Registry](https://github.com/mycordaapp/registry#readme)
* The [Commons](https://github.com/mycordaapp/commons#readme) module
* The [Commons](https://github.com/mycordaapp/commons#readme) module
  
* The [Really Simple Serialisation(rss)](https://github.com/mycordaapp/really-simple-serialisation#readme) module
    - [Jackson](https://github.com/FasterXML/jackson) for JSON serialisation

## Next Steps

