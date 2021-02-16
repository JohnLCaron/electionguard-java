/*
 * Copyright (c) 1998-2019 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package com.sunya.electionguard.viz;

import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.publish.CloseableIterable;
import com.sunya.electionguard.publish.CloseableIterator;
import ucar.ui.prefs.BeanTable;
import ucar.ui.widget.BAMutil;
import ucar.ui.widget.IndependentWindow;
import ucar.ui.widget.TextHistoryPane;
import ucar.util.prefs.PreferencesExt;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;

public class PlaintextTallyTable extends JPanel {
  private PreferencesExt prefs;

  private BeanTable<PlaintextTallyBean> tallyTable;
  private BeanTable<ContestBean> contestTable;
  private BeanTable<SelectionBean> selectionTable;

  private JSplitPane split1, split2;

  private TextHistoryPane infoTA;
  private IndependentWindow infoWindow;

  public PlaintextTallyTable(PreferencesExt prefs) {
    this.prefs = prefs;

    tallyTable = new BeanTable<>(PlaintextTallyBean.class, (PreferencesExt) prefs.node("TallyTable"), false);
    tallyTable.addListSelectionListener(e -> {
      PlaintextTallyBean tallyBean = tallyTable.getSelectedBean();
      if (tallyBean != null) {
        setTally(tallyBean);
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
    split1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, tallyTable, contestTable);
    split1.setDividerLocation(prefs.getInt("splitPos1", 200));

    split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, selectionTable);
    split2.setDividerLocation(prefs.getInt("splitPos2", 200));

    setLayout(new BorderLayout());
    add(split2, BorderLayout.CENTER);
  }

  void setPlaintextTallies(CloseableIterable<PlaintextTally> tallies) {
    try (CloseableIterator<PlaintextTally> iter = tallies.iterator())  {
      java.util.List<PlaintextTallyBean> beanList = new ArrayList<>();
      while (iter.hasNext()) {
        PlaintextTally tally = iter.next();
        beanList.add(new PlaintextTallyBean(tally));
      }
      tallyTable.setBeans(beanList);
      if (beanList.size() > 0) {
        setTally(beanList.get(0));
      } else {
        contestTable.setBeans(new ArrayList<>());
        selectionTable.setBeans(new ArrayList<>());
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  void setTally(PlaintextTallyBean plaintextTallyBean) {
    java.util.List<ContestBean> beanList = new ArrayList<>();
    for (PlaintextTally.Contest c : plaintextTallyBean.tally.contests.values()) {
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
    for (PlaintextTally.Selection s : contestBean.contest.selections().values()) {
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
  }

  public class PlaintextTallyBean {
    PlaintextTally tally;

    public PlaintextTallyBean(){}

    PlaintextTallyBean(PlaintextTally tally) {
      this.tally = tally;
    }

    public String getId() {
      return tally.object_id;
    }
  }

  public class ContestBean {
    PlaintextTally.Contest contest;

    public ContestBean(){}

    ContestBean(PlaintextTally.Contest contest) {
      this.contest = contest;
    }
    public String getContestId() {
      return contest.object_id();
    }
  }

  public class SelectionBean {
    PlaintextTally.Selection selection;

    public SelectionBean(){}

    SelectionBean(PlaintextTally.Selection selection) {
      this.selection = selection;
    }

    public String getSelectionId() {
      return selection.object_id();
    }

    public int getTally() {
      return selection.tally();
    }

    public int getNShares() {
      return selection.shares().size();
    }

  }

}
