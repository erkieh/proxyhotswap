proxyhotswap
============

Java Proxy hotswap agent to be used with a <a href="https://github.com/dcevm/dcevm">DCEVM</a> enabled JVM.

When an interface a proxy implements is modified (method(s) added, removed or changed), the java agent regenerates its class definition and reinitializes its static fields.
