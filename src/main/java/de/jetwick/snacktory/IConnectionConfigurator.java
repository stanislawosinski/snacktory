package de.jetwick.snacktory;

import java.net.HttpURLConnection;

/**
 * Performs additional configuration of the {@link HttpURLConnection} before the
 * request is made.
 */
public interface IConnectionConfigurator
{
    /**
     * An {@link IConnectionConfigurator} that does nothing.
     */
    public static final IConnectionConfigurator NOOP = new IConnectionConfigurator()
    {
        @Override
        public void configure(HttpURLConnection connection)
        {
        }
    };
    
    public void configure(HttpURLConnection connection);
}
