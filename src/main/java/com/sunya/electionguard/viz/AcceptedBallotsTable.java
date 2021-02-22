/*
 * Copyright (c) 1998-2019 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package com.sunya.electionguard.viz;

import com.sunya.electionguard.CiphertextAcceptedBallot;
import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.publish.CloseableIterable;
import com.sunya.electionguard.publish.CloseableIterator;
import ucar.ui.prefs.BeanTable;
import ucar.ui.widget.BAMutil;
import ucar.ui.widget.IndependentWindow;
import ucar.ui.widget.TextHistoryPane;
import ucar.util.prefs.PreferencesExt;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;

public class AcceptedBallotsTable extends JPanel {
  private final PreferencesExt prefs;

  private final BeanTable<CiphertextAcceptedBallotBean> ballotTable;
  private final BeanTable<ContestBean> contestTable;
  private final BeanTable<SelectionBean> selectionTable;

  private final JSplitPane split1, split2;
  private final IndependentWindow infoWindow;

  public AcceptedBallotsTable(PreferencesExt prefs) {
    this.prefs = prefs;
    TextHistoryPane infoTA = new TextHistoryPane();
    infoWindow = new IndependentWindow("Extra Information", BAMutil.getImage("electionguard-logo.png"), infoTA);
    infoWindow.setBounds((Rectangle) prefs.getBean("InfoWindowBounds", new Rectangle(300, 300, 800, 100)));

    ballotTable = new BeanTable<>(CiphertextAcceptedBallotBean.class, (PreferencesExt) prefs.node("BallotTable"), false);
    ballotTable.addListSelectionListener(e -> {
      CiphertextAcceptedBallotBean ballot = ballotTable.getSelectedBean();
      if (ballot != null) {
        setBallot(ballot);
      }
    });
    ballotTable.addPopupOption("Show Ballot", ballotTable.makeShowAction(infoTA, infoWindow,
            bean -> ((CiphertextAcceptedBallotBean)bean).ballot.toString()));

    contestTable = new BeanTable<>(ContestBean.class, (PreferencesExt) prefs.node("ContestTable"), false);
    contestTable.addListSelectionListener(e -> {
      ContestBean contest = contestTable.getSelectedBean();
      if (contest != null) {
        setContest(contest);
      }
    });
    contestTable.addPopupOption("Show Contest", contestTable.makeShowAction(infoTA, infoWindow,
            bean -> ((ContestBean)bean).contest.toString()));

    selectionTable = new BeanTable<>(SelectionBean.class, (PreferencesExt) prefs.node("SelectionTable"), false);
    selectionTable.addPopupOption("Show Selection", selectionTable.makeShowAction(infoTA, infoWindow,
            bean -> ((SelectionBean)bean).selection.toString()));

    // layout
    split1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, ballotTable, contestTable);
    split1.setDividerLocation(prefs.getInt("splitPos1", 200));

    split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, selectionTable);
    split2.setDividerLocation(prefs.getInt("splitPos2", 200));

    setLayout(new BorderLayout());
    add(split2, BorderLayout.CENTER);
  }

  void setAcceptedBallots(CloseableIterable<CiphertextAcceptedBallot> acceptedBallots) {
    try (CloseableIterator<CiphertextAcceptedBallot> iter = acceptedBallots.iterator())  {
      java.util.List<CiphertextAcceptedBallotBean> beanList = new ArrayList<>();
      while (iter.hasNext()) {
        CiphertextAcceptedBallot ballot = iter.next();
        beanList.add(new CiphertextAcceptedBallotBean(ballot));
      }
      ballotTable.setBeans(beanList);
      if (beanList.size() > 0) {
        setBallot(beanList.get(0));
      } else {
        contestTable.setBeans(new ArrayList<>());
        selectionTable.setBeans(new ArrayList<>());
      }
    }
  }

  void setBallot(CiphertextAcceptedBallotBean ballotBean) {
    java.util.List<ContestBean> beanList = new ArrayList<>();
    for (CiphertextBallot.Contest c : ballotBean.ballot.contests) {
      beanList.add(new ContestBean(c));
    }
    contestTable.setBeans(beanList);
    if (beanList.size() > 0) {
      setContest(beanList.get(0));
    } else {
      selectionTable.setBeans(new ArrayList<>());
    }
  }

  void setContest(ContestBean contestBean) {
    java.util.List<SelectionBean> beanList = new ArrayList<>();
    for (CiphertextBallot.Selection s : contestBean.contest.ballot_selections) {
      beanList.add(new SelectionBean(s));
    }
    selectionTable.setBeans(beanList);
  }

  void save() {
    ballotTable.saveState(false);
    contestTable.saveState(false);
    selectionTable.saveState(false);
    prefs.putBeanObject("InfoWindowBounds", infoWindow.getBounds());
    prefs.putInt("splitPos1", split1.getDividerLocation());
    prefs.putInt("splitPos2", split2.getDividerLocation());
  }

  public static class CiphertextAcceptedBallotBean {
    CiphertextAcceptedBallot ballot;

    public CiphertextAcceptedBallotBean(){}

    CiphertextAcceptedBallotBean(CiphertextAcceptedBallot ballot) {
      this.ballot = ballot;
    }

    public String getId() {
      return ballot.object_id;
    }

    public String getTracking() {
      return ballot.tracking_hash.toString();
    }

    public String getPrevTracking() {
      return ballot.previous_tracking_hash.toString();
    }

    public String getState() {
      return ballot.state.toString();
    }

    public String getStyle() {
      return ballot.ballot_style;
    }

    public String getTimeStamp() {
      return OffsetDateTime.ofInstant(Instant.ofEpochSecond(ballot.timestamp), ZoneId.of("UTC")).toString();
    }

    public boolean isNonce() {
      return ballot.nonce.isPresent();
    }

  }

  public static class ContestBean {
    CiphertextBallot.Contest contest;

    public ContestBean(){}

    ContestBean(CiphertextBallot.Contest contest) {
      this.contest = contest;
    }

    public String getContestId() {
      return contest.object_id;
    }

    public boolean isNonce() {
      return contest.nonce.isPresent();
    }

    public boolean isProof() {
      return contest.proof.isPresent();
    }

  }

  public static class SelectionBean {
    CiphertextBallot.Selection selection;

    public SelectionBean(){}

    SelectionBean(CiphertextBallot.Selection selection) {
      this.selection = selection;
    }

    public String getSelectionId() {
      return selection.object_id;
    }

    public String getCryptoHash() {
      return selection.crypto_hash.toString();
    }

    public boolean isPlaceHolder() {
      return selection.is_placeholder_selection;
    }

    public boolean isNonce() {
      return selection.nonce.isPresent();
    }

    public boolean isProof() {
      return selection.proof.isPresent();
    }

  }

}
