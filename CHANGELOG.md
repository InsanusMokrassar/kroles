# Changelog

## 0.0.5

* Add `AccessChecker` with RWM, callback and rights-only checks and logical composition
* Add `AccessChecker` entry points for role lists, repositories and Ktor helpers; deprecate `RWMRole.checkRights`
* Exclude the cached `rightsChecker` from `AccessChecker.RWM` serialization so it is rebuilt from `accessRights`
* Fix `RWMRole.AccessRights.contains` to require every requested access flag
* Change `RWMRole.AccessRights` to a data class with flag-based equality and hash codes while preserving its original string serialization
* Document that RWM flag checks ignore character order, repetitions and unrecognized characters: `rw`, `wr`, `rrw` and `wrx` represent the same rights
* Correct write/manage flag mapping in `RWMRole` factories and align `checkRights` Boolean arguments with read/write/manage order
* Align identifier filtering with `AccessChecker.RWM`: a null identifier accepts any role identifier, while a non-null identifier requires an exact match
* Change `BaseRole` to a data class while preserving its plain-string serialization
* Add tests for role and access-rights serialization, access checks, checker composition and repository integration; expand role-checking documentation
* Add regression tests for custom `RightsChecker` overload dispatch
* Enable parallel Gradle tooling sync
* Dependencies:
    * `Kotlin Coroutines`: `1.11.0` (tests)

## 0.0.4

* Dependencies:
    * `Kotlin`: `2.3.21` -> `2.4.21`
    * `NMCP`: `1.6.1` -> `1.6.2`
    * `KSLog`: `1.6.1` -> `2.1.0`
    * `MicroUtils`: `0.30.0` -> `0.32.0`

## 0.0.3

* Update `CacheRolesRepo` cache after successful write operations before returning

## 0.0.2

* Add `Dokka` documentation aggregation module
* Add publishing to Maven Central via `NMCP`
* Add missing KDocs
* Dependencies:
    * `Dokka`: `1.9.10` -> `2.2.0`
    * `NMCP`: `1.5.0`
    * `Kotlin`: `1.9.22` -> `2.3.21`
    * `Kotlin Serialization`: `1.6.2` -> `1.11.0`
    * `MicroUtils`: `0.20.34` -> `0.30.0`
    * `KSLog`: `1.3.2` -> `1.6.1`
    * `JUnit`: `4.12` -> `4.13.2`
    * `Android Test JUnit`: `1.1.5` -> `1.3.0`
    * `Android Espresso`: `3.5.1` -> `3.7.0`
    * `Android Gradle Plugin`: `8.2.2` -> `8.12.+`
    * `Android Compile SDK`: `34` -> `36`
    * `Android Build Tools`: `34.0.0` -> `36.0.0`
    * `Gradle`: `8.6` -> `9.3.0`

## 0.0.1

Project initialization
