package com.domidodo.logx.sdk.core;

import io.grpc.*;

public class HeaderClientInterceptor implements ClientInterceptor {

    private final Metadata metadata;

    public HeaderClientInterceptor(Metadata metadata) {
        this.metadata = metadata;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(
                next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.merge(metadata);
                super.start(responseListener, headers);
            }
        };
    }
}

