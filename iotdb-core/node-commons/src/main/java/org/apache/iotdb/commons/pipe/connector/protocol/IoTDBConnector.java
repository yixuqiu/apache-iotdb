/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.commons.pipe.connector.protocol;

import org.apache.iotdb.common.rpc.thrift.TEndPoint;
import org.apache.iotdb.commons.pipe.connector.PipeReceiverStatusHandler;
import org.apache.iotdb.commons.utils.NodeUrlUtils;
import org.apache.iotdb.pipe.api.PipeConnector;
import org.apache.iotdb.pipe.api.customizer.configuration.PipeConnectorRuntimeConfiguration;
import org.apache.iotdb.pipe.api.customizer.parameter.PipeParameterValidator;
import org.apache.iotdb.pipe.api.customizer.parameter.PipeParameters;
import org.apache.iotdb.pipe.api.exception.PipeParameterNotValidException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.CONNECTOR_EXCEPTION_CONFLICT_RECORD_IGNORED_DATA_DEFAULT_VALUE;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.CONNECTOR_EXCEPTION_CONFLICT_RECORD_IGNORED_DATA_KEY;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.CONNECTOR_EXCEPTION_CONFLICT_RESOLVE_STRATEGY_DEFAULT_VALUE;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.CONNECTOR_EXCEPTION_CONFLICT_RESOLVE_STRATEGY_KEY;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.CONNECTOR_EXCEPTION_CONFLICT_RETRY_MAX_TIME_SECONDS_DEFAULT_VALUE;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.CONNECTOR_EXCEPTION_CONFLICT_RETRY_MAX_TIME_SECONDS_KEY;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.CONNECTOR_EXCEPTION_OTHERS_RECORD_IGNORED_DATA_DEFAULT_VALUE;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.CONNECTOR_EXCEPTION_OTHERS_RECORD_IGNORED_DATA_KEY;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.CONNECTOR_EXCEPTION_OTHERS_RETRY_MAX_TIME_SECONDS_DEFAULT_VALUE;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.CONNECTOR_EXCEPTION_OTHERS_RETRY_MAX_TIME_SECONDS_KEY;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.CONNECTOR_IOTDB_BATCH_MODE_ENABLE_DEFAULT_VALUE;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.CONNECTOR_IOTDB_BATCH_MODE_ENABLE_KEY;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.CONNECTOR_IOTDB_HOST_KEY;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.CONNECTOR_IOTDB_IP_KEY;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.CONNECTOR_IOTDB_NODE_URLS_KEY;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.CONNECTOR_IOTDB_PORT_KEY;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.CONNECTOR_LOAD_BALANCE_ROUND_ROBIN_STRATEGY;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.CONNECTOR_LOAD_BALANCE_STRATEGY_KEY;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.CONNECTOR_LOAD_BALANCE_STRATEGY_SET;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.SINK_EXCEPTION_CONFLICT_RECORD_IGNORED_DATA_KEY;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.SINK_EXCEPTION_CONFLICT_RESOLVE_STRATEGY_KEY;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.SINK_EXCEPTION_CONFLICT_RETRY_MAX_TIME_SECONDS_KEY;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.SINK_EXCEPTION_OTHERS_RECORD_IGNORED_DATA_KEY;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.SINK_EXCEPTION_OTHERS_RETRY_MAX_TIME_SECONDS_KEY;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.SINK_IOTDB_BATCH_MODE_ENABLE_KEY;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.SINK_IOTDB_HOST_KEY;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.SINK_IOTDB_IP_KEY;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.SINK_IOTDB_NODE_URLS_KEY;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.SINK_IOTDB_PORT_KEY;
import static org.apache.iotdb.commons.pipe.config.constant.PipeConnectorConstant.SINK_LOAD_BALANCE_STRATEGY_KEY;

public abstract class IoTDBConnector implements PipeConnector {

  private static final String PARSE_URL_ERROR_FORMATTER =
      "Exception occurred while parsing node urls from target servers: {}";
  private static final String PARSE_URL_ERROR_MESSAGE =
      "Error occurred while parsing node urls from target servers, please check the specified 'host':'port' or 'node-urls'";

  private static final Logger LOGGER = LoggerFactory.getLogger(IoTDBConnector.class);

  protected final List<TEndPoint> nodeUrls = new ArrayList<>();

  protected String loadBalanceStrategy;

  protected boolean isTabletBatchModeEnabled = true;

  protected PipeReceiverStatusHandler receiverStatusHandler;

  @Override
  public void validate(PipeParameterValidator validator) throws Exception {
    final PipeParameters parameters = validator.getParameters();

    validator.validate(
        args ->
            (boolean) args[0]
                || (((boolean) args[1] || (boolean) args[2]) && (boolean) args[3])
                || (boolean) args[4]
                || (((boolean) args[5] || (boolean) args[6]) && (boolean) args[7]),
        String.format(
            "One of %s, %s:%s, %s, %s:%s must be specified",
            CONNECTOR_IOTDB_NODE_URLS_KEY,
            CONNECTOR_IOTDB_HOST_KEY,
            CONNECTOR_IOTDB_PORT_KEY,
            SINK_IOTDB_NODE_URLS_KEY,
            SINK_IOTDB_HOST_KEY,
            SINK_IOTDB_PORT_KEY),
        parameters.hasAttribute(CONNECTOR_IOTDB_NODE_URLS_KEY),
        parameters.hasAttribute(CONNECTOR_IOTDB_IP_KEY),
        parameters.hasAttribute(CONNECTOR_IOTDB_HOST_KEY),
        parameters.hasAttribute(CONNECTOR_IOTDB_PORT_KEY),
        parameters.hasAttribute(SINK_IOTDB_NODE_URLS_KEY),
        parameters.hasAttribute(SINK_IOTDB_IP_KEY),
        parameters.hasAttribute(SINK_IOTDB_HOST_KEY),
        parameters.hasAttribute(SINK_IOTDB_PORT_KEY));

    loadBalanceStrategy =
        parameters
            .getStringOrDefault(
                Arrays.asList(CONNECTOR_LOAD_BALANCE_STRATEGY_KEY, SINK_LOAD_BALANCE_STRATEGY_KEY),
                CONNECTOR_LOAD_BALANCE_ROUND_ROBIN_STRATEGY)
            .trim()
            .toLowerCase();
    validator.validate(
        arg -> CONNECTOR_LOAD_BALANCE_STRATEGY_SET.contains(loadBalanceStrategy),
        String.format(
            "Load balance strategy should be one of %s, but got %s.",
            CONNECTOR_LOAD_BALANCE_STRATEGY_SET, loadBalanceStrategy),
        loadBalanceStrategy);

    validator.validate(
        arg -> arg.equals("retry") || arg.equals("ignore"),
        String.format(
            "The value of key %s or %s must be either 'retry' or 'ignore'.",
            CONNECTOR_EXCEPTION_CONFLICT_RESOLVE_STRATEGY_KEY,
            SINK_EXCEPTION_CONFLICT_RESOLVE_STRATEGY_KEY),
        parameters
            .getStringOrDefault(
                Arrays.asList(
                    CONNECTOR_EXCEPTION_CONFLICT_RESOLVE_STRATEGY_KEY,
                    SINK_EXCEPTION_CONFLICT_RESOLVE_STRATEGY_KEY),
                CONNECTOR_EXCEPTION_CONFLICT_RESOLVE_STRATEGY_DEFAULT_VALUE)
            .trim()
            .toLowerCase());
  }

  @Override
  public void customize(PipeParameters parameters, PipeConnectorRuntimeConfiguration configuration)
      throws Exception {
    nodeUrls.clear();
    nodeUrls.addAll(parseNodeUrls(parameters));
    LOGGER.info("IoTDBConnector nodeUrls: {}", nodeUrls);

    isTabletBatchModeEnabled =
        parameters.getBooleanOrDefault(
            Arrays.asList(CONNECTOR_IOTDB_BATCH_MODE_ENABLE_KEY, SINK_IOTDB_BATCH_MODE_ENABLE_KEY),
            CONNECTOR_IOTDB_BATCH_MODE_ENABLE_DEFAULT_VALUE);
    LOGGER.info("IoTDBConnector isTabletBatchModeEnabled: {}", isTabletBatchModeEnabled);

    receiverStatusHandler =
        new PipeReceiverStatusHandler(
            parameters
                .getStringOrDefault(
                    Arrays.asList(
                        CONNECTOR_EXCEPTION_CONFLICT_RESOLVE_STRATEGY_KEY,
                        SINK_EXCEPTION_CONFLICT_RESOLVE_STRATEGY_KEY),
                    CONNECTOR_EXCEPTION_CONFLICT_RESOLVE_STRATEGY_DEFAULT_VALUE)
                .trim()
                .equalsIgnoreCase("retry"),
            parameters.getLongOrDefault(
                Arrays.asList(
                    CONNECTOR_EXCEPTION_CONFLICT_RETRY_MAX_TIME_SECONDS_KEY,
                    SINK_EXCEPTION_CONFLICT_RETRY_MAX_TIME_SECONDS_KEY),
                CONNECTOR_EXCEPTION_CONFLICT_RETRY_MAX_TIME_SECONDS_DEFAULT_VALUE),
            parameters.getBooleanOrDefault(
                Arrays.asList(
                    CONNECTOR_EXCEPTION_CONFLICT_RECORD_IGNORED_DATA_KEY,
                    SINK_EXCEPTION_CONFLICT_RECORD_IGNORED_DATA_KEY),
                CONNECTOR_EXCEPTION_CONFLICT_RECORD_IGNORED_DATA_DEFAULT_VALUE),
            parameters.getLongOrDefault(
                Arrays.asList(
                    CONNECTOR_EXCEPTION_OTHERS_RETRY_MAX_TIME_SECONDS_KEY,
                    SINK_EXCEPTION_OTHERS_RETRY_MAX_TIME_SECONDS_KEY),
                CONNECTOR_EXCEPTION_OTHERS_RETRY_MAX_TIME_SECONDS_DEFAULT_VALUE),
            parameters.getBooleanOrDefault(
                Arrays.asList(
                    CONNECTOR_EXCEPTION_OTHERS_RECORD_IGNORED_DATA_KEY,
                    SINK_EXCEPTION_OTHERS_RECORD_IGNORED_DATA_KEY),
                CONNECTOR_EXCEPTION_OTHERS_RECORD_IGNORED_DATA_DEFAULT_VALUE));
  }

  protected LinkedHashSet<TEndPoint> parseNodeUrls(PipeParameters parameters)
      throws PipeParameterNotValidException {
    final LinkedHashSet<TEndPoint> givenNodeUrls = new LinkedHashSet<>(nodeUrls);

    try {
      if (parameters.hasAttribute(CONNECTOR_IOTDB_IP_KEY)
          && parameters.hasAttribute(CONNECTOR_IOTDB_PORT_KEY)) {
        givenNodeUrls.add(
            new TEndPoint(
                parameters.getStringByKeys(CONNECTOR_IOTDB_IP_KEY),
                parameters.getIntByKeys(CONNECTOR_IOTDB_PORT_KEY)));
      }

      if (parameters.hasAttribute(SINK_IOTDB_IP_KEY)
          && parameters.hasAttribute(SINK_IOTDB_PORT_KEY)) {
        givenNodeUrls.add(
            new TEndPoint(
                parameters.getStringByKeys(SINK_IOTDB_IP_KEY),
                parameters.getIntByKeys(SINK_IOTDB_PORT_KEY)));
      }

      if (parameters.hasAttribute(CONNECTOR_IOTDB_HOST_KEY)
          && parameters.hasAttribute(CONNECTOR_IOTDB_PORT_KEY)) {
        givenNodeUrls.add(
            new TEndPoint(
                parameters.getStringByKeys(CONNECTOR_IOTDB_HOST_KEY),
                parameters.getIntByKeys(CONNECTOR_IOTDB_PORT_KEY)));
      }

      if (parameters.hasAttribute(SINK_IOTDB_HOST_KEY)
          && parameters.hasAttribute(SINK_IOTDB_PORT_KEY)) {
        givenNodeUrls.add(
            new TEndPoint(
                parameters.getStringByKeys(SINK_IOTDB_HOST_KEY),
                parameters.getIntByKeys(SINK_IOTDB_PORT_KEY)));
      }

      if (parameters.hasAttribute(CONNECTOR_IOTDB_NODE_URLS_KEY)) {
        givenNodeUrls.addAll(
            NodeUrlUtils.parseTEndPointUrls(
                Arrays.asList(
                    parameters.getStringByKeys(CONNECTOR_IOTDB_NODE_URLS_KEY).split(","))));
      }

      if (parameters.hasAttribute(SINK_IOTDB_NODE_URLS_KEY)) {
        givenNodeUrls.addAll(
            NodeUrlUtils.parseTEndPointUrls(
                Arrays.asList(parameters.getStringByKeys(SINK_IOTDB_NODE_URLS_KEY).split(","))));
      }
    } catch (Exception e) {
      LOGGER.warn(PARSE_URL_ERROR_FORMATTER, e.toString());
      throw new PipeParameterNotValidException(PARSE_URL_ERROR_MESSAGE);
    }

    checkNodeUrls(givenNodeUrls);

    return givenNodeUrls;
  }

  private void checkNodeUrls(Set<TEndPoint> nodeUrls) throws PipeParameterNotValidException {
    for (TEndPoint nodeUrl : nodeUrls) {
      if (Objects.isNull(nodeUrl.ip) || nodeUrl.ip.isEmpty()) {
        LOGGER.warn(PARSE_URL_ERROR_FORMATTER, "host cannot be empty");
        throw new PipeParameterNotValidException(PARSE_URL_ERROR_MESSAGE);
      }
      if (nodeUrl.port == 0) {
        LOGGER.warn(PARSE_URL_ERROR_FORMATTER, "port cannot be empty");
        throw new PipeParameterNotValidException(PARSE_URL_ERROR_MESSAGE);
      }
    }
  }

  public PipeReceiverStatusHandler statusHandler() {
    return receiverStatusHandler;
  }
}
