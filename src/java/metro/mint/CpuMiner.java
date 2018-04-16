/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2017 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Nxt software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */
package metro.mint;

import metro.Constants;
import metro.Metro;
import metro.http.API;
import metro.http.GetWork;
import metro.util.Convert;
import metro.util.Logger;
import metro.util.TrustAllSSLProvider;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static metro.Consensus.HASH_FUNCTION;

public class CpuMiner {

    public static final int NONCE_BYTE_SIZE = 8;

    public static void main(String[] args) {
        CpuMiner cpuMiner = new CpuMiner();
        cpuMiner.mine();
    }

    private void mine() {
        boolean isSubmitted = Metro.getBooleanProperty("metro.mine.isSubmitted");
        boolean isStopOnError = Metro.getBooleanProperty("metro.mine.stopOnError");

        JSONObject miningTarget = getMiningTarget();
        JSONObject resultObject = (JSONObject) miningTarget.get("result");
        byte[] targetReverted = Convert.parseHexString((String) resultObject.get("target"));
        byte[] target = revertByteArray(targetReverted);

        byte[] data = Convert.parseHexString((String) resultObject.get("data"));
        GetWork.reverseEvery4Bytes(data);
        int size = (int) readDataSize(data);
        data = Arrays.copyOf(data,size);
        long initialNonce = Metro.getIntProperty("metro.mine.initialNonce");

        int threadPoolSize = Metro.getIntProperty("metro.mine.threadPoolSize");
        if (threadPoolSize == 0) {
            threadPoolSize = Runtime.getRuntime().availableProcessors();
            Logger.logDebugMessage("Thread pool size " + threadPoolSize);
        }
        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        Logger.logInfoMessage("Mine worker started");
        long counter = 0;
        while (true) {
            counter++;
            try {
                JSONObject response = mineImpl(counter, data, target, initialNonce, threadPoolSize, executorService, isSubmitted);
                Logger.logInfoMessage("mine response:" + response.toJSONString());
            } catch (Exception e) {
                Logger.logInfoMessage("mine error", e);
                if (isStopOnError) {
                    Logger.logInfoMessage("stopping on error");
                    break;
                } else {
                    Logger.logInfoMessage("continue");
                }
            }
            miningTarget = getMiningTarget();
            resultObject = (JSONObject) miningTarget.get("result");
            targetReverted = Convert.parseHexString((String) resultObject.get("target"));
            target = revertByteArray(targetReverted);
        }
    }

    private long readDataSize(byte[] data) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        byte[] sizeData = Arrays.copyOfRange(data,data.length - 8, data.length);
        buffer.put(sizeData);
        buffer.flip();
        return buffer.getLong()/8;
    }

    private JSONObject mineImpl(long counter, byte[] data, byte[] target, long initialNonce, int threadPoolSize,
                                ExecutorService executorService, boolean isSubmitted) {
        long startTime = System.currentTimeMillis();
        byte[] dataWithoutNonce = Arrays.copyOf(data,data.length - NONCE_BYTE_SIZE);
        List<Callable<Integer>> workersList = new ArrayList<>();
        for (int i = 0; i < threadPoolSize; i++) {
            CpuMiner.HashSolver hashSolver = new CpuMiner.HashSolver(dataWithoutNonce, initialNonce + i, target, threadPoolSize);
            workersList.add(hashSolver);
        }
        int solution = solve(executorService, workersList);
        long computationTime = System.currentTimeMillis() - startTime;
        if (computationTime == 0) {
            computationTime = 1;
        }
        long hashes = solution - initialNonce;
        Logger.logInfoMessage("solution nonce %d counter %d computed hashes %d time [sec] %.2f hash rate [KH/Sec] %d is submitted %b",
                solution, counter, hashes, (float) computationTime / 1000, hashes / computationTime, isSubmitted);
        JSONObject response;
        if (isSubmitted) {
            response = submitSolution(dataWithoutNonce, solution);
        } else {
            response = new JSONObject();
            response.put("message", "metro.mine.isSubmitted=false therefore solution is not submitted");
        }
        return response;
    }

    private int solve(Executor executor, Collection<Callable<Integer>> solvers) {
        CompletionService<Integer> ecs = new ExecutorCompletionService<>(executor);
        List<Future<Integer>> futures = new ArrayList<>(solvers.size());
        solvers.forEach(solver -> futures.add(ecs.submit(solver)));
        try {
            return ecs.take().get();
        } catch (ExecutionException | InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            for (Future<Integer> f : futures) {
                f.cancel(true);
            }
        }
    }

    private JSONObject submitSolution(byte[] data, int nonce) {
        Map<String, String> urlParams = new HashMap<>();
        urlParams.put("requestType", "getWork");
        JSONObject requestBody = new JSONObject();
        JSONArray paramsArray = new JSONArray();
        ByteBuffer buffer = ByteBuffer.allocate(NONCE_BYTE_SIZE + data.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(data);
        buffer.putLong(nonce);
        String solution = Convert.toHexString(buffer.array());
        paramsArray.add(solution);
        requestBody.put("params", paramsArray);
        return getJsonResponse(urlParams, requestBody);
    }

    private JSONObject getMiningTarget() {
        Map<String, String> params = new HashMap<>();
        params.put("requestType", "getWork");
        return getJsonResponse(params, null);
    }

    private JSONObject getJsonResponse(Map<String, String> params, JSONObject requestBody) {
        JSONObject response;
        HttpURLConnection connection = null;
        String host = Convert.emptyToNull(Metro.getStringProperty("metro.mine.serverAddress"));
        if (host == null) {
            try {
                host = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                host = "localhost";
            }
        }
        String protocol = "http";
        boolean useHttps = Metro.getBooleanProperty("metro.mine.useHttps");
        if (useHttps) {
            protocol = "https";
            HttpsURLConnection.setDefaultSSLSocketFactory(TrustAllSSLProvider.getSslSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(TrustAllSSLProvider.getHostNameVerifier());
        }
        int port = Constants.isTestnet ? API.TESTNET_API_PORT : Metro.getIntProperty("metro.apiServerPort");
        String urlParams = getUrlParams(params);
        Authenticator.setDefault (new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(Metro.getStringProperty("metro.mine.user"), Metro.getStringProperty("metro.mine.password").toCharArray());
            }
        });
        URL url;
        try {
            url = new URL(protocol, host, port, "/metro?" + urlParams);
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
        try {
            Logger.logDebugMessage("Sending request to server: " + url.toString());
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            if (requestBody != null) {
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");
                OutputStreamWriter wr = new OutputStreamWriter(connection.getOutputStream());
                wr.write(requestBody.toString());
                wr.flush();
            }

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (Reader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                    response = (JSONObject) JSONValue.parse(reader);
                }
            } else {
                response = null;
            }
        } catch (RuntimeException | IOException e) {
            Logger.logInfoMessage("Error connecting to server", e);
            if (connection != null) {
                connection.disconnect();
            }
            throw new IllegalStateException(e);
        }
        if (response == null) {
            throw new IllegalStateException(String.format("Request %s response error", url));
        }
        if (response.get("errorCode") != null) {
            throw new IllegalStateException(String.format("Request %s produced error response code %s message \"%s\"",
                    url, response.get("errorCode"), response.get("errorDescription")));
        }
        if (response.get("error") != null) {
            throw new IllegalStateException(String.format("Request %s produced error %s",
                    url, response.get("error")));
        }
        return response;
    }

    private static String getUrlParams(Map<String, String> params) {
        if (params == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String key : params.keySet()) {
            try {
                sb.append(key).append("=").append(URLEncoder.encode(params.get(key), "utf8")).append("&");
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }
        String rc = sb.toString();
        if (rc.endsWith("&")) {
            rc = rc.substring(0, rc.length() - 1);
        }
        return rc;
    }

    private static class HashSolver implements Callable<Integer> {

        private final long nonce;
        private final byte[] target;
        private final int poolSize;
        private final byte[] data;

        private HashSolver(byte[] data, long nonce,
                           byte[] target, int poolSize) {
            this.data = data;
            this.nonce = nonce;
            this.target = target;
            this.poolSize = poolSize;
        }

        @Override
        public Integer call() {
            int n = 0;

            while (!Thread.currentThread().isInterrupted()) {
                ByteBuffer buffer = ByteBuffer.allocate(data.length + NONCE_BYTE_SIZE);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.put(data);
                buffer.putLong(n);

                byte[] hash = HASH_FUNCTION.hash(buffer.array());
                if (new BigInteger(1, hash).compareTo(new BigInteger(1, target)) < 0) {
                    Logger.logDebugMessage("%s found solution hash %s nonce %d" +
                                    " hash %s meets target %d",
                            Thread.currentThread().getName(), HASH_FUNCTION, n,
                            Arrays.toString(hash), new BigInteger(target));
                    return n;
                }
                n += poolSize;
                if (n > 256L * 256L * 256L * 256L) {
                    Logger.logInfoMessage("%s solution not found for nonce within %d upto 2^32", Thread.currentThread().getName(), nonce);
                }
                if (((n - nonce) % (poolSize * 1000000)) == 0) {
                    Logger.logInfoMessage("%s computed %d [MH]", Thread.currentThread().getName(), (n - nonce) / poolSize / 1000000);
                }
            }
            return null;
        }
    }

    private static byte[] revertByteArray(byte[] array) {
        int i = 0;
        int j = array.length - 1;
        byte tmp;
        while (j > i) {
            tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            j--;
            i++;
        }
        return array;
    }
}
