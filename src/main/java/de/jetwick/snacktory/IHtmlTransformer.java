package de.jetwick.snacktory;

/**
 * Transforms input HTML into some other HTML. Implementations must not make any
 * assumptions about validity of the input HTML. Implementations may convert the input
 * HTML into a corresponding XML stream and return transformed XML.
 */
public interface IHtmlTransformer
{
    /**
     * An identity {@link IHtmlTransformer} that returns the input tag soup unchanged.
     */
    public static final IHtmlTransformer IDENTITY = new IHtmlTransformer()
    {
        @Override
        public String transform(String tagsoup, String encoding)
        {
            return tagsoup;
        }
    };

    public String transform(String tagsoup, String encoding);
}
