/*
 * Copyright (c) 1998-2019 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package com.sunya.electionguard.viz;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.CiphertextTally;
import ucar.ui.prefs.BeanTable;
import ucar.ui.widget.BAMutil;
import ucar.ui.widget.IndependentWindow;
import ucar.ui.widget.TextHistoryPane;
import ucar.util.prefs.PreferencesExt;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;

public class CiphertextTallyTable extends JPanel {
  private final PreferencesExt prefs;

  private final BeanTable<CiphertextTallyBean> tallyTable;
  private final BeanTable<ContestBean> contestTable;
  private final BeanTable<SelectionBean> selectionTable;

  private final JSplitPane split1, split2;
  private final IndependentWindow infoWindow;

  public CiphertextTallyTable(PreferencesExt prefs) {
    this.prefs = prefs;
    TextHistoryPane infoTA = new TextHistoryPane();
    infoWindow = new IndependentWindow("Extra Information", BAMutil.getImage("electionguard-logo.png"), infoTA);
    infoWindow.setBounds((Rectangle) prefs.getBean("InfoWindowBounds", new Rectangle(300, 300, 800, 100)));

    tallyTable = new BeanTable<>(CiphertextTallyBean.class, (PreferencesExt) prefs.node("TallyTable"), false,
            "CiphertextTally", "encrypted_tally", null);
    tallyTable.addPopupOption("Show Tally", tallyTable.makeShowAction(infoTA, infoWindow,
            bean -> ((CiphertextTallyBean)bean).tally.toString()));

    contestTable = new BeanTable<>(ContestBean.class, (PreferencesExt) prefs.node("ContestTable"), false,
            "Contest", "CiphertextTally.Contest", null);
    contestTable.addListSelectionListener(e -> {
      ContestBean contest = contestTable.getSelectedBean();
      if (contest != null) {
        setContest(contest);
      }
    });
    contestTable.addPopupOption("Show Contest", contestTable.makeShowAction(infoTA, infoWindow,
            bean -> ((ContestBean)bean).contest.toString()));

    selectionTable = new BeanTable<>(SelectionBean.class, (PreferencesExt) prefs.node("SelectionTable"), false,
            "Selection", "CiphertextTally.Selection", null);
    selectionTable.addPopupOption("Show Selection", selectionTable.makeShowAction(infoTA, infoWindow,
            bean -> ((SelectionBean)bean).selection.toString()));

    // layout
    split1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, tallyTable, contestTable);
    split1.setDividerLocation(prefs.getInt("splitPos1", 200));

    split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, selectionTable);
    split2.setDividerLocation(prefs.getInt("splitPos2", 200));

    setLayout(new BorderLayout());
    add(split2, BorderLayout.CENTER);
  }

  void setCiphertextTally(CiphertextTally plaintextTally) {
    tallyTable.setBeans(ImmutableList.of(new CiphertextTallyBean(plaintextTally)));

    java.util.List<ContestBean> beanList = new ArrayList<>();
    for (CiphertextTally.Contest c : plaintextTally.contests.values()) {
      beanList.add(new ContestBean(c));
    }
    contestTable.setBeans(beanList);
    selectionTable.setBeans(new ArrayList<>());
  }

  void setContest(ContestBean contestBean) {
    java.util.List<SelectionBean> beanList = new ArrayList<>();
    for (CiphertextTally.Selection s : contestBean.contest.selections.values()) {
      beanList.add(new SelectionBean(s));
    }
    selectionTable.setBeans(beanList);
  }

  void save() {
    tallyTable.saveState(false);
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

  public class CiphertextTallyBean {
    CiphertextTally tally;

    public CiphertextTallyBean(){}

    CiphertextTallyBean(CiphertextTally tally) {
      this.tally = tally;
    }

    public String getId() {
      return tally.object_id;
    }
  }

  public class ContestBean {
    CiphertextTally.Contest contest;

    public ContestBean(){}

    ContestBean(CiphertextTally.Contest contest) {
      this.contest = contest;
    }
    public String getContestId() {
      return contest.object_id;
    }
    public String getContestDescriptionHash() {
      return contest.contestDescriptionHash.toString();
    }
  }

  public class SelectionBean {
    CiphertextTally.Selection selection;

    public SelectionBean(){}

    SelectionBean(CiphertextTally.Selection selection) {
      this.selection = selection;
    }

    public String getSelectionId() {
      return selection.object_id;
    }

    public String getDescriptionHash() {
      return selection.description_hash.toString();
    }

    public String getCiphertextPad() {
      return selection.ciphertext().pad.toShortString();
    }

    public String getCiphertextData() {
      return selection.ciphertext().data.toShortString();
    }

  }

}
