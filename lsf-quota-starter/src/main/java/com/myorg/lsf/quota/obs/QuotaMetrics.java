package com.myorg.lsf.quota.obs;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public class QuotaMetrics {
    private final Counter reserveAccepted;
    private final Counter reserveRejected;
    private final Counter reserveDuplicate;

    private final Counter confirmOk;
    private final Counter confirmNotFound;

    private final Counter releaseOk;
    private final Counter releaseNotFound;

    public QuotaMetrics(MeterRegistry r, String app, String backend) {
        this.reserveAccepted = Counter.builder("lsf.quota.reserve").tag("outcome", "accepted").tag("app", app).tag("backend", backend).register(r);
        this.reserveRejected  = Counter.builder("lsf.quota.reserve").tag("outcome", "rejected").tag("app", app).tag("backend", backend).register(r);
        this.reserveDuplicate = Counter.builder("lsf.quota.reserve").tag("outcome", "duplicate").tag("app", app).tag("backend", backend).register(r);

        this.confirmOk        = Counter.builder("lsf.quota.confirm").tag("outcome", "ok").tag("app", app).tag("backend", backend).register(r);
        this.confirmNotFound  = Counter.builder("lsf.quota.confirm").tag("outcome", "not_found").tag("app", app).tag("backend", backend).register(r);

        this.releaseOk        = Counter.builder("lsf.quota.release").tag("outcome", "ok").tag("app", app).tag("backend", backend).register(r);
        this.releaseNotFound  = Counter.builder("lsf.quota.release").tag("outcome", "not_found").tag("app", app).tag("backend", backend).register(r);
    }

    public void incReserveAccepted() { reserveAccepted.increment(); }
    public void incReserveRejected() { reserveRejected.increment(); }
    public void incReserveDuplicate() { reserveDuplicate.increment(); }

    public void incConfirmOk() { confirmOk.increment(); }
    public void incConfirmNotFound() { confirmNotFound.increment(); }

    public void incReleaseOk() { releaseOk.increment(); }
    public void incReleaseNotFound() { releaseNotFound.increment(); }
}