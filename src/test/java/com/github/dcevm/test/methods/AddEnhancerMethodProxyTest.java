package com.github.dcevm.test.methods;

import static com.github.dcevm.test.util.HotSwapTestHelper.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.Before;
import org.junit.Test;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

public class AddEnhancerMethodProxyTest {
	
	static public class DummyHandler implements InvocationHandler {
		private Object a;
		
		public DummyHandler(Object a) {
			this.a = a;
		}
		
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			return method.invoke(a, args);
		}
	}
	
	// Version 0
	public static class AImpl implements A {
		public int getValue1() {
			return 1;
		}
	}
	
	// Version 1
	public static class AImpl___1 implements A___1 {
		public int getValue2() {
			return 2;
		}
	}
	
	// Version 0
	public interface A {
		public int getValue1();
	}
	
	// Version 1
	public interface A___1 {
		public int getValue2();
	}
	
	@Before
	public void setUp() throws Exception {
		__toVersion__(0);
	}
	
	@Test
	public void addMethodToInterfaceAndImplementation() throws IllegalAccessException, IllegalArgumentException,
			InvocationTargetException {
		
		assert __version__() == 0;
		
		final A a = new AImpl();
		
		assertEquals(1, a.getValue1());
		
		__toVersion__(1);
		
		Method method = getMethod(a, "getValue2");
		assertEquals(2, method.invoke(a, null));
	}
	
	public static class SerializableNoOp implements Serializable, MethodInterceptor {
		private Enhancer target;
		
		public SerializableNoOp(Enhancer target) {
			this.target = target;
		}
		
		@Override
		public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
			System.out.println("interceptor");
			return method.invoke(new AImpl(), args);
		}
	}
	
	@Test
	public void accessNewMethodOnProxy() throws IllegalAccessException, IllegalArgumentException,
			InvocationTargetException {
		
		assert __version__() == 0;
		
		final A a = createEnhancer();
		
		assertEquals(1, a.getValue1());
		
		__toVersion__(1);
		Method method = getMethod(a, "getValue2");
		assertEquals("getValue2", method.getName());
		assertEquals(2, method.invoke(a, null));
	}
	
	private A createEnhancer() {
		Enhancer enhancer = new Enhancer();
		enhancer.setSuperclass(AImpl.class);
		// CGLIB$CALLBACK_0 CglibAopProxy$DynamicAdvisedInterceptor (id=3799)
		// CGLIB$CALLBACK_1 CglibAopProxy$StaticUnadvisedInterceptor (id=3800)
		// CGLIB$CALLBACK_2 CglibAopProxy$SerializableNoOp (id=3801)
		// CGLIB$CALLBACK_3 CglibAopProxy$StaticDispatcher (id=3802)
		// CGLIB$CALLBACK_4 CglibAopProxy$AdvisedDispatcher (id=3803)
		// CGLIB$CALLBACK_5 CglibAopProxy$EqualsInterceptor (id=3804)
		// CGLIB$CALLBACK_6 CglibAopProxy$HashCodeInterceptor (id=3808)
		
		enhancer.setCallback(new SerializableNoOp(enhancer));
		final A a = (A) enhancer.create();
		return a;
	}
	
	@Test
	public void accessNewMethodOnProxyCreatedAfterSwap() throws IllegalAccessException, IllegalArgumentException,
			InvocationTargetException, IOException {
		assert __version__() == 0;
		A a = createEnhancer();
		
		assertEquals(1, a.getValue1());
		__toVersion__(1);
		
		a = createEnhancer();
		
		Method method = getMethod(a, "getValue2");
		assertEquals("getValue2", method.getName());
		assertEquals(2, method.invoke(a, null));
	}
	
	private Method getMethod(Object a, String methodName) {
		Method[] declaredMethods = a.getClass().getMethods();
		Method m = null;
		for (Method method : declaredMethods) {
			if (method.getName().equals(methodName))
				m = method;
		}
		if (m == null) {
			fail(a.getClass().getSimpleName() + " does not have method " + methodName);
		}
		return m;
	}
	
}