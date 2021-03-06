/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.hydromatic.optiq.impl;

import net.hydromatic.optiq.Table;
import net.hydromatic.optiq.TranslatableTable;

import org.eigenbase.rel.*;
import org.eigenbase.relopt.*;
import org.eigenbase.reltype.RelDataType;
import org.eigenbase.reltype.RelDataTypeFactory;
import org.eigenbase.sql.validate.SqlValidatorUtil;
import org.eigenbase.util.ImmutableIntList;
import org.eigenbase.util.Pair;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

/**
 * Virtual table that is composed of two or more tables joined together.
 *
 * <p>Star tables do not occur in end-user queries. They are introduced by the
 * optimizer to help matching queries to materializations, and used only
 * during the planning process.</p>
 *
 * <p>When a materialization is defined, if it involves a join, it is converted
 * to a query on top of a star table. Queries that are candidates to map onto
 * the materialization are mapped onto the same star table.</p>
 */
public class StarTable extends AbstractTable implements TranslatableTable {
  // TODO: we'll also need a list of join conditions between tables. For now
  //  we assume that join conditions match
  public final ImmutableList<Table> tables;

  /** Number of fields in each table's row type. */
  public ImmutableIntList fieldCounts;

  /** Creates a StarTable. */
  public StarTable(List<Table> tables) {
    super();
    this.tables = ImmutableList.copyOf(tables);
  }

  /** Creates a StarTable and registers it in a schema. */
  public static StarTable of(List<Table> tables) {
    return new StarTable(tables);
  }

  public RelDataType getRowType(RelDataTypeFactory typeFactory) {
    final List<RelDataType> typeList = new ArrayList<RelDataType>();
    final List<String> nameList = new ArrayList<String>();
    final List<Integer> fieldCounts = new ArrayList<Integer>();
    for (Table table : tables) {
      final RelDataType rowType = table.getRowType(typeFactory);
      typeList.addAll(RelOptUtil.getFieldTypeList(rowType));
      nameList.addAll(rowType.getFieldNames());
      fieldCounts.add(rowType.getFieldCount());
    }
    // Compute fieldCounts the first time this method is called. Safe to assume
    // that the field counts will be the same whichever type factory is used.
    if (this.fieldCounts == null) {
      this.fieldCounts = ImmutableIntList.copyOf(fieldCounts);
    }
    return typeFactory.createStructType(typeList,
        SqlValidatorUtil.uniquify(nameList));
  }

  public RelNode toRel(RelOptTable.ToRelContext context, RelOptTable table) {
    // Create a table scan of infinite cost.
    return new StarTableScan(context.getCluster(), table);
  }

  public StarTable add(Table table) {
    final List<Table> tables1 = new ArrayList<Table>(tables);
    tables1.add(table);
    return of(tables1);
  }

  /** Returns the column offset of the first column of {@code table} in this
   * star table's output row type.
   *
   * @param table Table
   * @return Column offset
   * @throws IllegalArgumentException if table is not in this star
   */
  public int columnOffset(Table table) {
    int n = 0;
    for (Pair<Table, Integer> pair : Pair.zip(tables, fieldCounts)) {
      if (pair.left == table) {
        return n;
      }
      n += pair.right;
    }
    throw new IllegalArgumentException("star table " + this
        + " does not contain table " + table);
  }

  /** Relational expression that scans a {@link StarTable}.
   *
   * <p>It has infinite cost.
   */
  private static class StarTableScan extends TableAccessRelBase {
    public StarTableScan(RelOptCluster cluster, RelOptTable relOptTable) {
      super(cluster, cluster.traitSetOf(Convention.NONE), relOptTable);
    }

    @Override public RelOptCost computeSelfCost(RelOptPlanner planner) {
      return planner.getCostFactory().makeInfiniteCost();
    }
  }
}

// End StarTable.java
