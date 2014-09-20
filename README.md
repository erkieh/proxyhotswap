proxyhotswap
============

Java Proxy hotswap agent to be used with a <a href="https://github.com/dcevm/dcevm">DCEVM</a> enabled JVM. Tests are in a different <a href="https://github.com/erkieh/proxyhotswap/tree/tests">branch</a> of the project, to a gain more flexibility in licensing.

When an interface a proxy implements is modified (method(s) added, removed or changed), the java agent regenerates its class definition and reinitializes its static fields.
