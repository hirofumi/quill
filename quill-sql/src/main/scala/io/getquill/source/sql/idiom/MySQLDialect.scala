package io.getquill.source.sql.idiom

import io.getquill.ast._
import io.getquill.util.Show._

object MySQLDialect
    extends SqlIdiom
    with OffsetWithoutLimitWorkaround {

  override def prepareKeyword = Some("FROM")

  override implicit def operationShow(implicit propertyShow: Show[Property]): Show[Operation] = new Show[Operation] {
    def show(e: Operation) =
      e match {
        case BinaryOperation(a, StringOperator.`+`, b) => s"CONCAT(${a.show}, ${b.show})"
        case other                                     => MySQLDialect.super.operationShow.show(other)
      }
  }
}
