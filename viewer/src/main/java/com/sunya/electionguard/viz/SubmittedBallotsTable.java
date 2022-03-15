/*
 * Copyright (c) 1998-2019 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package com.sunya.electionguard.viz;

import com.sunya.electionguard.InternalManifest;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.SubmittedBallot;
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
import java.util.Optional;

public class SubmittedBallotsTable extends JPanel {
  private final PreferencesExt prefs;

  private final BeanTable<SubmittedBallotBean> ballotTable;
  private final BeanTable<ContestBean> contestTable;
  private final BeanTable<SelectionBean> selectionTable;

  private final JSplitPane split1, split2;
  private final IndependentWindow infoWindow;

  private InternalManifest manifest;

  public SubmittedBallotsTable(PreferencesExt prefs) {
    this.prefs = prefs;
    TextHistoryPane infoTA = new TextHistoryPane();
    infoWindow = new IndependentWindow("Extra Information", BAMutil.getImage("electionguard-logo.png"), infoTA);
    infoWindow.setBounds((Rectangle) prefs.getBean("InfoWindowBounds", new Rectangle(300, 300, 800, 100)));

    ballotTable = new BeanTable<>(SubmittedBallotBean.class, (PreferencesExt) prefs.node("BallotTable"), false,
            "SubmittedBallot", "encrypted_ballots", null);
    ballotTable.addListSelectionListener(e -> {
      SubmittedBallotBean ballot = ballotTable.getSelectedBean();
      if (ballot != null) {
        setBallot(ballot);
      }
    });
    ballotTable.addPopupOption("Show Ballot", ballotTable.makeShowAction(infoTA, infoWindow,
            bean -> ((SubmittedBallotBean)bean).ballot.toString()));

    contestTable = new BeanTable<>(ContestBean.class, (PreferencesExt) prefs.node("ContestTable"), false,
            "Contest", "CiphertextBallot.Contest", null);
    contestTable.addListSelectionListener(e -> {
      ContestBean contest = contestTable.getSelectedBean();
      if (contest != null) {
        setContest(contest);
      }
    });
    contestTable.addPopupOption("Show Contest", contestTable.makeShowAction(infoTA, infoWindow,
            bean -> bean.toString()));

    selectionTable = new BeanTable<>(SelectionBean.class, (PreferencesExt) prefs.node("SelectionTable"), false,
            "Selection", "CiphertextBallot.Selection", null);
    selectionTable.addPopupOption("Show Selection", selectionTable.makeShowAction(infoTA, infoWindow,
            bean -> bean.toString()));

    // layout
    split1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, ballotTable, contestTable);
    split1.setDividerLocation(prefs.getInt("splitPos1", 200));

    split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, selectionTable);
    split2.setDividerLocation(prefs.getInt("splitPos2", 200));

    setLayout(new BorderLayout());
    add(split2, BorderLayout.CENTER);
  }

  void setAcceptedBallots(Manifest manifest, CloseableIterable<SubmittedBallot> acceptedBallots) {
    this.manifest = new InternalManifest(manifest);
    try (CloseableIterator<SubmittedBallot> iter = acceptedBallots.iterator())  {
      java.util.List<SubmittedBallotBean> beanList = new ArrayList<>();
      while (iter.hasNext()) {
        SubmittedBallot ballot = iter.next();
        beanList.add(new SubmittedBallotBean(ballot));
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

  void setBallot(SubmittedBallotBean ballotBean) {
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
    for (CiphertextBallot.Selection s : contestBean.contest.selections) {
      beanList.add(new SelectionBean(s, contestBean));
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

  public static class SubmittedBallotBean {
    SubmittedBallot ballot;

    public SubmittedBallotBean(){}

    SubmittedBallotBean(SubmittedBallot ballot) {
      this.ballot = ballot;
    }

    public String getId() {
      return ballot.object_id();
    }

    public String getTracking() {
      return ballot.code.toString();
    }

    public String getPrevTracking() {
      return ballot.code_seed.toString();
    }

    public String getState() {
      return ballot.state.toString();
    }

    public String getStyle() {
      return ballot.ballotStyleId;
    }

    public String getTimeStamp() {
      return OffsetDateTime.ofInstant(Instant.ofEpochSecond(ballot.timestamp), ZoneId.of("UTC")).toString();
    }

    public boolean isNonce() {
      return ballot.nonce.isPresent();
    }

  }

  public class ContestBean {
    CiphertextBallot.Contest contest;
    Optional<InternalManifest.ContestWithPlaceholders> descOpt;

    public ContestBean(){}

    ContestBean(CiphertextBallot.Contest contest) {
      this.contest = contest;
      this.descOpt= manifest.getContestById(contest.contestId);
    }

    public String getContestId() {
      return contest.contestId;
    }

    public boolean isNonce() {
      return contest.nonce.isPresent();
    }

    public boolean isProof() {
      return contest.proof.isPresent();
    }

    public String getName() {
      return descOpt.map( desc -> desc.contest.name()).orElse( "N/A");
    }

    public String getVoteVariation() {
      return descOpt.map( desc -> desc.contest.voteVariation().toString()).orElse( "N/A");
    }

    @Override
    public String toString() {
      return String.format("CiphertextBallot.Contest%n %s%n%nManifest.ContestDescription%n%s%n",
              contest , descOpt.map( d -> d.contest.toString()).orElse("N/A"));
    }
  }

  public class SelectionBean {
    CiphertextBallot.Selection selection;
    Optional<Manifest.SelectionDescription> descOpt;

    public SelectionBean(){}

    SelectionBean(CiphertextBallot.Selection selection, ContestBean contestBean) {
      this.selection = selection;
      if (contestBean.descOpt.isPresent()) {
        InternalManifest.ContestWithPlaceholders contestDesc = contestBean.descOpt.get();
        this.descOpt = contestDesc.getSelectionById(selection.selectionId);
      } else {
        this.descOpt = Optional.empty();
      }
    }

    public String getSelectionId() {
      return selection.object_id();
    }

    public String getCandidateId() {
      return descOpt.map( desc -> desc.candidateId()).orElse( "N/A");
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

    @Override
    public String toString() {
      return String.format("CiphertextBallot.Selection%n %s%n%nManifest.SelectionDescription%n%s%n",
              selection , descOpt.map(d -> d.toString()).orElse("N/A"));
    }
  }

}
