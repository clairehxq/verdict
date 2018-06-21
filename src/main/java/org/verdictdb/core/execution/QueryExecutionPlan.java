/*
 * Copyright 2018 University of Michigan
 * 
 * You must contact Barzan Mozafari (mozafari@umich.edu) or Yongjoo Park (pyongjoo@umich.edu) to discuss
 * how you could use, modify, or distribute this code. By default, this code is not open-sourced and we do
 * not license this code.
 */

package org.verdictdb.core.execution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingDeque;

import org.apache.commons.lang3.tuple.Pair;
import org.verdictdb.connection.DbmsConnection;
import org.verdictdb.core.DbmsMetaDataCache;
import org.verdictdb.core.query.AbstractRelation;
import org.verdictdb.core.query.BaseTable;
import org.verdictdb.core.query.JoinTable;
import org.verdictdb.core.query.SelectQueryOp;
import org.verdictdb.core.rewriter.ScrambleMeta;
import org.verdictdb.core.sql.NonValidatingSQLParser;
import org.verdictdb.exception.UnexpectedTypeException;
import org.verdictdb.exception.ValueException;
import org.verdictdb.exception.VerdictDbException;
import org.verdictdb.sql.syntax.SyntaxAbstract;

public class QueryExecutionPlan {
  
  SelectQueryOp query;
  
  ScrambleMeta scrambleMeta;
  
  QueryExecutionNode root;
  
//  PostProcessor postProcessor;
  
//  /**
//   * 
//   * @param queryString A select query
//   * @throws UnexpectedTypeException 
//   */
//  public AggQueryExecutionPlan(DbmsConnection conn, SyntaxAbstract syntax, String queryString) throws VerdictDbException {
//    this(conn, syntax, (SelectQueryOp) new NonValidatingSQLParser().toRelation(queryString));
//  }
  
  /**
   * 
   * @param query  A well-formed select query object
   * @throws ValueException 
   * @throws VerdictDbException 
   */
  public QueryExecutionPlan(
      DbmsConnection conn, 
      SyntaxAbstract syntax, 
      ScrambleMeta scrambleMeta, 
      SelectQueryOp query) throws VerdictDbException {
    this.scrambleMeta = scrambleMeta;
    if (!query.isAggregateQuery()) {
      throw new UnexpectedTypeException(query);
    }
    this.query = query;
    this.root = makePlan(conn, syntax, query);
//    this.postProcessor = plan.getRight();
  }
  
  /** 
   * Creates a tree in which each node is AggQueryExecutionNode. Each AggQueryExecutionNode corresponds to
   * an aggregate query, whether it is the main query or a subquery.
   * 
   * 1. Restrict the aggregate subqueries to appear only in the where clause.
   * 2. If an aggregate subquery appears in the where clause, the subquery itself should be a single
   *    AggQueryExecutionNode even if it contains another aggregate subqueries within it.
   * 3. Except for the root nodes, all other nodes are not approximated.
   * 4. AggQueryExecutionNode must not include any correlated predicates.
   * 5. The results of intermediate AggQueryExecutionNode should be stored as a materialized view.
   * 
   * @param conn
   * @param query
   * @return Pair of roots of the tree and post-processing interface.
   * @throws ValueException 
   * @throws UnexpectedTypeException 
   */
  // check whether query contains scramble table in its from list
  Boolean checkScrambleTable(List<AbstractRelation> fromlist) {
    for (AbstractRelation table:fromlist) {
      if (table instanceof BaseTable) {
        if (scrambleMeta.isScrambled(((BaseTable) table).getSchemaName(), ((BaseTable) table).getTableName())) {
          return true;
        }
      }
      else if (table instanceof JoinTable) {
        if (checkScrambleTable(((JoinTable) table).getJoinList())) {
          return true;
        }
      }
    }
    return false;
  }

  // TODO
  QueryExecutionNode makePlan(DbmsConnection conn, SyntaxAbstract syntax, SelectQueryOp query) 
      throws VerdictDbException {
    // check whether outer query has scramble table stored in scrambleMeta
    boolean scrambleTableinOuterQuery = checkScrambleTable(query.getFromList());

    // identify aggregate subqueries and create separate nodes for them
    List<AbstractRelation> subqueryToReplace = new ArrayList<>();
    List<SelectQueryOp> rootToReplace = new ArrayList<>();
    for (AbstractRelation table:query.getFromList()) {
      if (table instanceof SelectQueryOp) {
        if (table.isAggregateQuery()) {
          subqueryToReplace.add(table);
        }
        else if (!scrambleTableinOuterQuery && checkScrambleTable(((SelectQueryOp) table).getFromList())) { // use inner query as root
          rootToReplace.add((SelectQueryOp) table);
        }
      }
      else if (table instanceof JoinTable) {
        for (AbstractRelation jointTable:((JoinTable) table).getJoinList()) {
          if (jointTable instanceof SelectQueryOp) {
            if (jointTable.isAggregateQuery()) {
              subqueryToReplace.add(jointTable);
            }
            else if (!scrambleTableinOuterQuery && checkScrambleTable(((SelectQueryOp) jointTable).getFromList())) { // use inner query as root
              rootToReplace.add((SelectQueryOp) jointTable);
            }
          }
        }
      }
    }
    // generate temp table names for those aggregate subqueries and use them in their ancestors.
    
    return new AsyncAggExecutionNode(conn, scrambleMeta, query);
  }
  
  public void execute(DbmsConnection conn) {
    // execute roots
    
    // after executions are all finished.
    cleanUp();
  }
  
  // clean up any intermediate materialized tables
  void cleanUp() {
    
  }

}