package com.pm.patientservice.grpc;

import billing.BillingRequest;
import billing.BillingResponse;
import billing.BillingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * gRPC client for the billing-service.
 *
 * For local runs WITHOUT the billing-service, set:
 *     billing.grpc.enabled=false
 * in application.properties. When disabled, this client skips the network call
 * and returns a dummy response, so patient-service runs standalone.
 */
@Service
public class BillingServiceGrpcClient {

  private static final Logger log = LoggerFactory.getLogger(
      BillingServiceGrpcClient.class);

  private final boolean enabled;
  private final BillingServiceGrpc.BillingServiceBlockingStub blockingStub;

  public BillingServiceGrpcClient(
      @Value("${billing.grpc.enabled:false}") boolean enabled,
      @Value("${billing.service.address:localhost}") String serverAddress,
      @Value("${billing.service.grpc.port:9001}") int serverPort) {

    this.enabled = enabled;

    if (!enabled) {
      log.warn("Billing gRPC is DISABLED (billing.grpc.enabled=false). "
          + "Patient creation will skip the billing account call.");
      this.blockingStub = null;
      return;
    }

    log.info("Connecting to Billing Service GRPC service at {}:{}",
        serverAddress, serverPort);

    ManagedChannel channel = ManagedChannelBuilder.forAddress(serverAddress,
        serverPort).usePlaintext().build();

    blockingStub = BillingServiceGrpc.newBlockingStub(channel);
  }

  public BillingResponse createBillingAccount(String patientId, String name,
      String email) {

    if (!enabled) {
      log.info("Skipping billing account creation (gRPC disabled) for patientId={}",
          patientId);
      return BillingResponse.newBuilder()
          .setAccountId("DISABLED")
          .setStatus("SKIPPED")
          .build();
    }

    BillingRequest request = BillingRequest.newBuilder().setPatientId(patientId)
        .setName(name).setEmail(email).build();

    BillingResponse response = blockingStub.createBillingAccount(request);
    log.info("Received response from billing service via GRPC: {}", response);
    return response;
  }
}
