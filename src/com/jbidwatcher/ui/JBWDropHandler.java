package com.jbidwatcher.ui;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.queue.DropQObject;
import com.jbidwatcher.util.queue.MessageQueue;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.DeletedEntry;
import com.jbidwatcher.auction.EntryCorral;
import com.jbidwatcher.auction.server.AuctionServer;

public class JBWDropHandler implements MessageQueue.Listener {
  private static JBWDropHandler sInstance = null;
  private static boolean do_uber_debug = false;
  private static String lastSeen = null;

  public void messageAction(Object deQ) {
    if (deQ instanceof String && StringTools.isNumberOnly((String)deQ)) {
      AuctionEntry ae = EntryCorral.getInstance().takeForRead((String) deQ);
      if (ae != null) {
        boolean lostAuction = ae.getAuction() == null;
        ae.update();
        if (lostAuction) AuctionsManager.getInstance().addEntry(ae);
        return;
      }
    }

    String auctionURL;
    String label;
    boolean interactive;

    if(deQ instanceof String) {
      auctionURL = deQ.toString();
      label = null;
      interactive = true;
    } else {
      DropQObject dObj = (DropQObject) deQ;
      auctionURL = (String) dObj.getData();
      label = dObj.getLabel();
      interactive = dObj.isInteractive();
    }

    loadDroppedEntry(auctionURL, label, interactive);
  }

  private void loadDroppedEntry(String auctionURL, String label, boolean interactive) {
    String aucId;

    if (do_uber_debug) {
      JConfig.log().logDebug("Dropping (action): " + auctionURL);
    }

    //  Check to see if it's got a protocol ({protocol}:{path})
    //  If not, treat it as an item number alone, in the space of the default auction server.
    if(auctionURL.indexOf(":") != -1) {
      AuctionServer aucServ = AuctionServerManager.getInstance().getServer();
      aucId = aucServ.extractIdentifierFromURLString(auctionURL);
    } else {
      aucId = auctionURL;
    }
    if(interactive) DeletedEntry.remove(aucId);

    //  We get the identifier from the URL (which is multi-country),
    //  then create an auction entry from the id.
    AuctionEntry aeNew = AuctionEntry.construct(aucId);
    if(aeNew != null && aeNew.isLoaded()) {
      if(label != null) aeNew.setCategory(label);
      aeNew.clearNeedsUpdate();
      JConfig.log().logDebug("Loaded " + aeNew.getIdentifier() + '.');
      lastSeen = aeNew.getIdentifier();
      AuctionsManager.getInstance().addEntry(aeNew);
    } else {
      if(lastSeen == null || !aucId.equals(lastSeen)) {
        JConfig.log().logDebug("Not loaded (" + aucId + ").");
        lastSeen = aucId;
      }
      if(aeNew != null) aeNew.delete();
    }
  }

  public static void start() {
    if(sInstance == null) MQFactory.getConcrete("drop").registerListener(sInstance = new JBWDropHandler());
  }
}
