package kittoku.osc


internal const val MAX_MRU = 2000
internal const val MAX_MTU = 2000
internal const val MIN_MRU = 68
internal const val MIN_MTU = 68
internal const val DEFAULT_MRU = 1500
internal const val DEFAULT_MTU = 1350 // 1430 = 1500 - 70 (IPv4+TCP+TLS1.3+SSTP+PPP), запас на PMTU оператора
