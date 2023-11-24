package io.jexxa.commons.wrapper.component.persistence.objectstore.imdb;



import io.jexxa.commons.wrapper.component.persistence.objectstore.INumericQuery;
import io.jexxa.commons.wrapper.component.persistence.objectstore.metadata.StringTag;
import io.jexxa.commons.wrapper.component.persistence.objectstore.IObjectStore;
import io.jexxa.commons.wrapper.component.persistence.objectstore.IStringQuery;
import io.jexxa.commons.wrapper.component.persistence.objectstore.metadata.MetadataSchema;
import io.jexxa.commons.wrapper.component.persistence.objectstore.metadata.NumericTag;
import io.jexxa.commons.wrapper.component.persistence.repository.imdb.IMDBRepository;
import io.jexxa.commons.wrapper.json.JSONManager;


import java.util.EnumSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("rawtypes")
public class IMDBObjectStore<T, K, M extends Enum<M> & MetadataSchema>  extends IMDBRepository<T, K> implements IObjectStore<T, K, M>
{
    private final Set<M> metaData;

    public IMDBObjectStore(
            Class<T> aggregateClazz,
            Function<T, K> keyFunction,
            Class<M> metaData,
            Properties properties
            )
    {
        super(aggregateClazz, keyFunction, properties);
        this.metaData = EnumSet.allOf(metaData);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> INumericQuery<T, S> getNumericQuery(M metaTag, Class<S> queryType)
    {
        if ( !metaData.contains(metaTag) )
        {
            throw new IllegalArgumentException("Unknown strategy for "+ metaTag.name());
        }

        //noinspection unchecked
        NumericTag<T, S> numericTag = (NumericTag) metaTag.getTag();

        return new IMDBNumericQuery<>(this, numericTag, queryType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> IStringQuery<T, S> getStringQuery(M metaTag, Class<S> queryType)
    {
        if ( !metaData.contains(metaTag) )
        {
            throw new IllegalArgumentException("Unknown strategy for " + metaTag.name());
        }

        //noinspection unchecked
        StringTag<T, S> stringTag = (StringTag) metaTag.getTag();

        return new IMDBStringQuery<>(this, stringTag, queryType);
    }


    Map<K, T> getAggregates()
    {
        Map<K, String> myggregateMap = getAggregateMap(getAggregateClazz());
        return myggregateMap
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> JSONManager.getJSONConverter().fromJson(entry.getValue(), getAggregateClazz())));
    }

}

