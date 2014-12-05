import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.name.Named;
import com.google.inject.spi.ProvisionListener;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class ProvisionListenerTest
{
	private static boolean POST_CONSTRUCT_WAS_CALLED = false;
	
	/**
	 * This test fails, showing that ProviderListener isn't made aware of exceptions in the object being constructed by the
	 * ProvisionInvocation
	 */
	@Test(expected = ProvisionException.class)
	public void testPostConstructNotCalledOnFailureInDependencyConstructor()
	{
		POST_CONSTRUCT_WAS_CALLED = false;

		Injector injector = Guice.createInjector(new TestModule());
		try
		{
			final ClassWithBrokenDependency instance = injector.getInstance(ClassWithBrokenDependency.class);

			assertNull("Creation of object should fail!", instance);
		}
		finally
		{
			assertFalse("postConstruct should not have been called", POST_CONSTRUCT_WAS_CALLED);
		}
	}


	/**
	 * This test works
	 */
	@Test(expected = ConfigurationException.class)
	public void testPostConstructNotCalledOnMissingBinding()
	{
		POST_CONSTRUCT_WAS_CALLED = false;

		Injector injector = Guice.createInjector(new TestModule());
		try
		{
			final ClassWithStringBinding instance = injector.getInstance(ClassWithStringBinding.class);

			assertNull("Creation of object should fail!", instance);
		}
		finally
		{
			assertFalse("postConstruct should not have been called", POST_CONSTRUCT_WAS_CALLED);
		}
	}


	/**
	 * Class that has a named binding; this binding is not present so the class should not be constructable by Guice
	 */
	static class ClassWithStringBinding implements PostConstructListener
	{
		@Inject
		@Named("xyz")
		String xyz;


		@Override
		public void postConstruct()
		{
			POST_CONSTRUCT_WAS_CALLED = true;
		}
	}

	/**
	 * Class that depends on another class; this other class will always throw an exception from its constructor so this class
	 * should not be constructable by Guice
	 */
	static class ClassWithBrokenDependency implements PostConstructListener
	{
		@Inject
		DodgyDependency dodgy;


		@Override
		public void postConstruct()
		{
			POST_CONSTRUCT_WAS_CALLED = true;
		}
	}

	/**
	 * A class whose constructor throws an exception
	 */
	static class DodgyDependency
	{
		@Inject
		DodgyDependency()
		{
			throw new RuntimeException();
		}
	}


	//
	// Interface, ProviderListener implementation and Module for basic postConstruct listener
	//
	static interface PostConstructListener
	{
		public void postConstruct();
	}

	static class TestProvisionListener implements ProvisionListener
	{

		@Override
		public <T> void onProvision(final ProvisionInvocation<T> provision)
		{
			final T instance = provision.provision();

			((PostConstructListener) instance).postConstruct();
		}
	}

	static class TestModule extends AbstractModule
	{

		@Override
		protected void configure()
		{
			bindListener(new AbstractMatcher<Binding<?>>()
			{

				@Override
				public boolean matches(final Binding<?> binding)
				{
					final Key<?> key = binding.getKey();
					final TypeLiteral<?> typeLiteral = key.getTypeLiteral();

					return PostConstructListener.class.isAssignableFrom(typeLiteral.getRawType());
				}
			}, new TestProvisionListener());
		}
	}
}
