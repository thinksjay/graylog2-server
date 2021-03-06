package org.graylog.storage.elasticsearch7;

import com.github.joschi.jadconfig.util.Duration;
import org.graylog.shaded.elasticsearch7.org.apache.http.HttpHost;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.ElasticsearchException;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.action.search.MultiSearchRequest;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.action.search.MultiSearchResponse;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.action.search.SearchRequest;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.action.search.SearchResponse;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.client.RequestOptions;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.client.RestClient;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.client.RestClientBuilder;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.client.RestHighLevelClient;
import org.graylog2.indexer.IndexNotFoundException;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

public class ElasticsearchClient {
    private final RestHighLevelClient client;

    @Inject
    public ElasticsearchClient(@Named("elasticsearch_hosts") List<URI> elasticsearchHosts,
                               @Named("elasticsearch_connect_timeout") Duration elasticsearchConnectTimeout,
                               @Named("elasticsearch_socket_timeout") Duration elasticsearchSocketTimeout,
                               @Named("elasticsearch_idle_timeout") Duration elasticsearchIdleTimeout,
                               @Named("elasticsearch_max_total_connections") int elasticsearchMaxTotalConnections,
                               @Named("elasticsearch_max_total_connections_per_route") int elasticsearchMaxTotalConnectionsPerRoute,
                               @Named("elasticsearch_max_retries") int elasticsearchMaxRetries,
                               @Named("elasticsearch_discovery_enabled") boolean discoveryEnabled,
                               @Named("elasticsearch_discovery_filter") @Nullable String discoveryFilter,
                               @Named("elasticsearch_discovery_frequency") Duration discoveryFrequency,
                               @Named("elasticsearch_discovery_default_scheme") String defaultSchemeForDiscoveredNodes,
                               @Named("elasticsearch_compression_enabled") boolean compressionEnabled) {
        final HttpHost[] esHosts = elasticsearchHosts.stream().map(uri -> new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme())).toArray(HttpHost[]::new);

        final RestClientBuilder restClientBuilder = RestClient.builder(esHosts)
                .setRequestConfigCallback(requestConfig -> requestConfig
                        .setConnectTimeout(Math.toIntExact(elasticsearchConnectTimeout.toMilliseconds()))
                        .setSocketTimeout(Math.toIntExact(elasticsearchSocketTimeout.toMilliseconds()))
                )
                .setHttpClientConfigCallback(httpClientConfig -> httpClientConfig
                        .setMaxConnTotal(elasticsearchMaxTotalConnections)
                        .setMaxConnPerRoute(elasticsearchMaxTotalConnectionsPerRoute)
                );

        this.client = new RestHighLevelClient(restClientBuilder);
    }

    public SearchResponse search(SearchRequest searchRequest, String errorMessage) {
        final MultiSearchRequest multiSearchRequest = new MultiSearchRequest()
                .add(searchRequest);

        final MultiSearchResponse result = this.execute((c, requestOptions) -> c.msearch(multiSearchRequest, requestOptions), errorMessage);

        return firstResponseFrom(result, errorMessage);
    }

    private SearchResponse firstResponseFrom(MultiSearchResponse result, String errorMessage) {
        checkArgument(result != null);
        checkArgument(result.getResponses().length == 1);

        final MultiSearchResponse.Item firstResponse = result.getResponses()[0];
        if (firstResponse.getResponse() == null) {
            throw exceptionFrom(firstResponse.getFailure(), errorMessage);
        }

        return firstResponse.getResponse();
    }

    public <R> R execute(ThrowingBiFunction<RestHighLevelClient, RequestOptions, R, IOException> fn) {
        return execute(fn, "An error occurred: ");
    }

    public <R> R execute(ThrowingBiFunction<RestHighLevelClient, RequestOptions, R, IOException> fn, String errorMessage) {
        try {
            return fn.apply(client, requestOptions());
        } catch (Exception e) {
            throw exceptionFrom(e, errorMessage);
        }
    }

    private RequestOptions requestOptions() {
        return RequestOptions.DEFAULT;
    }

    private ElasticsearchException exceptionFrom(Exception e, String errorMessage) {
        if (e instanceof ElasticsearchException) {
            final ElasticsearchException elasticsearchException = (ElasticsearchException)e;
            if (isIndexNotFoundException(elasticsearchException)) {
                throw IndexNotFoundException.create(errorMessage + elasticsearchException.getResourceId(), elasticsearchException.getIndex().getName());
            }
        }
        return new ElasticsearchException(errorMessage, e);
    }

    private boolean isIndexNotFoundException(ElasticsearchException e) {
        return e.getMessage().contains("index_not_found_exception");
    }
}
