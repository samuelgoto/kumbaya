package com.kumbaya.monitor;

import com.google.inject.AbstractModule;
import com.google.inject.matcher.Matchers;

public class VarZModule extends AbstractModule {
	@Override
	protected void configure() {
		VarZInterceptor interceptor = new VarZInterceptor();
		bindInterceptor(Matchers.any(), 
				Matchers.annotatedWith(VarZ.class ), 
				interceptor);
		requestInjection(interceptor);
	}
}
