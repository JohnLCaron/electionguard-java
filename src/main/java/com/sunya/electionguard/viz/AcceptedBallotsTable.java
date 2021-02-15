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
import ucar.ui.widget.FileManager;
import ucar.ui.widget.IndependentWindow;
import ucar.ui.widget.TextHistoryPane;
import ucar.util.prefs.PreferencesExt;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;

public class AcceptedBallotsTable extends JPanel {
  private FileManager fileChooser;
  private PreferencesExt prefs;

  private BeanTable<CiphertextAcceptedBallotBean> ballotTable;
  private BeanTable<ContestBean> contestTable;
  private BeanTable<SelectionBean> selectionTable;

  private JSplitPane split1, split2;

  private TextHistoryPane infoTA;
  private IndependentWindow infoWindow;

  public AcceptedBallotsTable(PreferencesExt prefs, FileManager fileChooser) {
    this.prefs = prefs;
    this.fileChooser = fileChooser;

    ballotTable = new BeanTable<>(CiphertextAcceptedBallotBean.class, (PreferencesExt) prefs.node("AcceptedBallotTable"), false);
    ballotTable.addListSelectionListener(e -> {
      CiphertextAcceptedBallotBean ballot = ballotTable.getSelectedBean();
      if (ballot != null) {
        setBallot(ballot);
      }
    });

    contestTable = new BeanTable<>(ContestBean.class, (PreferencesExt) prefs.node("ContestTable"), false);
    contestTable.addListSelectionListener(e -> {
      ContestBean contest = contestTable.getSelectedBean();
      if (contest != null) {
        setContest(contest);
      }
    });

    selectionTable = new BeanTable<>(SelectionBean.class, (PreferencesExt) prefs.node("SelectionTable"), false);

    // the info window
    infoTA = new TextHistoryPane();
    infoWindow = new IndependentWindow("Extra Information", BAMutil.getImage("electionguard-logo.png"), infoTA);
    infoWindow.setBounds((Rectangle) prefs.getBean("InfoWindowBounds", new Rectangle(300, 300, 800, 100)));

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
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  void setBallot(CiphertextAcceptedBallotBean ballotBean) {
    java.util.List<ContestBean> beanList = new ArrayList<>();
    for (CiphertextBallot.Contest c : ballotBean.ballot.contests) {
      beanList.add(new ContestBean(c));
    }
    contestTable.setBeans(beanList);
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

  void showInfo(Formatter f) {
    f.format(" Current time =   %s%n%n", new Date().toString());

    /* int n = 0;
    if (completeLogs != null) {
      n = completeLogs.size();
      f.format("Complete logs n=%d%n", n);
      f.format("  first log date= %s%n", completeLogs.get(0).getDate());
      f.format("   last log date= %s%n", completeLogs.get(n - 1).getDate());
    }
    List restrict = mergeTable.getBeans();
    if (restrict != null && (restrict.size() != n)) {
      f.format("%nRestricted, merged logs n=%d%n", restrict.size());
    }

    if (logFiles != null) {
      f.format("%nFiles used%n");
      for (LogLocalManager.FileDateRange fdr : logFiles) {
        f.format(" %s [%s,%s]%n", fdr.f.getName(), fdr.start, fdr.end);
      }
    } */
  }

  public class CiphertextAcceptedBallotBean {
    CiphertextAcceptedBallot ballot;

    public CiphertextAcceptedBallotBean(){}

    CiphertextAcceptedBallotBean(CiphertextAcceptedBallot ballot) {
      this.ballot = ballot;
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
  }

  public class ContestBean {
    CiphertextBallot.Contest contest;

    public ContestBean(){}

    ContestBean(CiphertextBallot.Contest contest) {
      this.contest = contest;
    }

    public String getContestId() {
      return contest.object_id;
    }

    public boolean isHasNonce() {
      return contest.nonce.isPresent();
    }

    public boolean isHasProof() {
      return contest.proof.isPresent();
    }

  }

  public class SelectionBean {
    CiphertextBallot.Selection selection;

    public SelectionBean(){}

    SelectionBean(CiphertextBallot.Selection selection) {
      this.selection = selection;
    }

    public String getSelectionId() {
      return selection.object_id;
    }

    public boolean isPlaceHolder() {
      return selection.is_placeholder_selection;
    }

    public boolean isHasNonce() {
      return selection.nonce.isPresent();
    }

    public boolean isHasProof() {
      return selection.proof.isPresent();
    }

  }

}
