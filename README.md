#proxyhotswap

Java Proxy hotswap agent to be used with a <a href="https://github.com/dcevm/dcevm">DCEVM</a> enabled JVM.

When an interface a java.lang.reflect.Proxy generated proxy implements is modified (method(s) added, removed or changed), the java agent regenerates its class definition and reinitializes its static fields. 

Also works for Cglib proxies. Cglib proxy replacement does not use package names to detect the Class definition generators to cope with repackaged libraries (like the Spring framework). The java agent transformer just checks interface names and method names instead. This may cause problems if you have a interface named GeneratorStrategy that has a method named generate. 

java.lang.reflect.ProxyCglib proxy replacement is a one step process, Cglib on the other hand, a two step one. So for Cglib proxies you may recieve exceptions when the classes are acccessed before the second step has finished. 


## Usage
Configure the Java VM with the -javaagent option.
For example if you already use DCEVM via `-XXaltjvm=dcevm` then you just need to append 
` -javaagent:C:\path_to_downloaded_file\java-proxy-agent-v0.0.1-alpha-3.jar`
so it will become
`-XXaltjvm=dcevm -javaagent:C:\path_to_downloaded_file\java-proxy-agent-v0.0.1-alpha-3.jar`

`-XXaltjvm=dcevm` option is not required for this agent, but DCEVM must be enabled.
