package com.haulmont.addon.imap.core.ext;

import com.sun.mail.imap.protocol.FetchItem;
import com.sun.mail.imap.protocol.FetchResponse;
import com.sun.mail.imap.protocol.Item;

import javax.mail.FetchProfile;

@SuppressWarnings("SpellCheckingInspection")
public class ThreadExtension {

    private static final String ITEM_NAME = "X-GM-THRID";
    public static final String CAPABILITY_NAME = "X-GM-EXT-1";
    private static final CubaProfileItem THREAD_ID_ITEM = new CubaProfileItem();
    public static final FetchItem FETCH_ITEM = new FetchItem(ITEM_NAME, THREAD_ID_ITEM) {
        @Override
        public Object parseItem(FetchResponse r) {
            return new X_GM_THRID(r);
        }
    };

    public static class X_GM_THRID implements Item {

        @SuppressWarnings({"unused"})
        final int seqnum;

        public final long x_gm_thrid;

        X_GM_THRID(FetchResponse r) {
            seqnum = r.getNumber();
            r.skipSpaces();
            x_gm_thrid = r.readLong();
        }


    }

    static class CubaProfileItem extends FetchProfile.Item {
        CubaProfileItem() {
            super(ITEM_NAME);
        }
    }

    public static class FetchProfileItem extends FetchProfile.Item {
        FetchProfileItem() {
            super(ThreadExtension.ITEM_NAME);
        }

        public static final FetchProfileItem X_GM_THRID = new FetchProfileItem();
    }
}
