/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.status;

import com.kismetsoftware.politigif.gif.Gif;
import com.kismetsoftware.politigif.report.Report;
import com.kismetsoftware.politigif.signin.SigninLink;
import com.kismetsoftware.politigif.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Gives a {@code status} to records written before the status field existed (local dev data from
 * 2026-11-17/18; production has none). Updates only, and only where {@code status} is missing, so
 * it is a no-op on every later boot. The older {@code hiddenByOwner}/{@code hiddenByAdmin} booleans
 * on GIFs are translated, then left in place (nothing is removed).
 */
@Component
public class StatusBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StatusBackfill.class);

    private final MongoTemplate mongo;

    public StatusBackfill(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments args) {
        long n = 0;
        // GIFs: admin hide wins; remember the owner's choice underneath it.
        n += set(Gif.class, Criteria.where("hiddenByAdmin").is(true).and("hiddenByOwner").is(true),
                new Update().set("status", GifStatus.HIDDEN_BY_ADMIN.code())
                        .set("statusBeforeAdminHide", GifStatus.HIDDEN_BY_OWNER.code()));
        n += set(Gif.class, Criteria.where("hiddenByAdmin").is(true),
                new Update().set("status", GifStatus.HIDDEN_BY_ADMIN.code())
                        .set("statusBeforeAdminHide", GifStatus.ACTIVE.code()));
        n += set(Gif.class, Criteria.where("hiddenByOwner").is(true),
                new Update().set("status", GifStatus.HIDDEN_BY_OWNER.code()));
        n += set(Gif.class, new Criteria(), new Update().set("status", GifStatus.ACTIVE.code()));
        n += set(User.class, new Criteria(), new Update().set("status", UserStatus.ACTIVE.code()));
        n += set(Report.class, new Criteria(), new Update().set("status", ReportStatus.ACTIVE.code()));
        n += set(SigninLink.class, Criteria.where("usedAt").ne(null),
                new Update().set("status", SigninLinkStatus.USED.code()));
        n += set(SigninLink.class, new Criteria(), new Update().set("status", SigninLinkStatus.ACTIVE.code()));
        if (n > 0) {
            log.info("status backfill: gave a status to {} records written before the status field", n);
        }
    }

    private long set(Class<?> type, Criteria criteria, Update update) {
        Query missing = Query.query(new Criteria().andOperator(Criteria.where("status").exists(false), criteria));
        return mongo.updateMulti(missing, update, type).getModifiedCount();
    }
}
