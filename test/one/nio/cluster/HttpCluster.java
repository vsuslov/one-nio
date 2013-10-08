package one.nio.cluster;

import one.nio.net.ConnectionString;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class HttpCluster extends WeightCluster<HttpProvider> {
    private static final Log log = LogFactory.getLog(HttpCluster.class);
    private static final int RETRIES = 3;

    protected synchronized List<HttpProvider> replaceProviders(Map<HttpProvider, Integer> newProviders) {
        ArrayList<HttpProvider> oldProviders = new ArrayList<HttpProvider>(providers.keySet());
        providers.clear();
        providers.putAll(newProviders);
        rebuildProviderSelector();
        return oldProviders;
    }

    public void configure(String configuration) throws IOException {
        HashMap<HttpProvider, Integer> newProviders = new HashMap<HttpProvider, Integer>();
        for (StringTokenizer st = new StringTokenizer(configuration); st.hasMoreElements(); ) {
            String host = st.nextToken();
            int weight = Integer.parseInt(st.nextToken());
            newProviders.put(new HttpProvider(new ConnectionString(host)), weight);
        }

        List<HttpProvider> oldProviders = replaceProviders(newProviders);
        for (HttpProvider provider : oldProviders) {
            provider.close();
        }
    }

    public String invoke(String uri) throws ServiceUnavailableException {
        for (int i = 0; i < RETRIES; i++) {
            HttpProvider provider = getProvider();
            try {
                return provider.invoke(uri);
            } catch (Exception e) {
                disableProvider(provider);
                log.warn(provider + " invocation failed", e);
            }
        }
        throw new ServiceUnavailableException("Cluster invocation failed after " + RETRIES + " retries");
    }
}