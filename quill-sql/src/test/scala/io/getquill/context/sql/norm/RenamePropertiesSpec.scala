package io.getquill.context.sql.norm

import io.getquill.Spec
import io.getquill.context.sql.testContext._
import io.getquill.context.sql.testContext

class RenamePropertiesSpec extends Spec {

  val e = quote {
    query[TestEntity].schema(_.entity("test_entity").columns(_.s -> "field_s", _.i -> "field_i"))
  }

  val f = quote {
    qr1.filter(t => t.i == 1)
  }

  "renames properties according to the entity aliases" - {
    "action" - {
      "insert" in {
        val q = quote {
          e.insert(lift(TestEntity("a", 1, 1L, None)))
        }
        testContext.run(q).string mustEqual
          "INSERT INTO test_entity (field_s,field_i,l,o) VALUES (?, ?, ?, ?)"
      }

      "insert assigned" in {
        val q = quote {
          e.insert(_.i -> 1, _.l -> 1L, _.o -> 1, _.s -> "test")
        }
        testContext.run(q).string mustEqual
          "INSERT INTO test_entity (field_i,l,o,field_s) VALUES (1, 1, 1, 'test')"
      }
      "update" in {
        val q = quote {
          e.filter(_.i == 999).update(lift(TestEntity("a", 1, 1L, None)))
        }
        testContext.run(q).string mustEqual
          "UPDATE test_entity SET field_s = ?, field_i = ?, l = ?, o = ? WHERE field_i = 999"
      }
      "delete" in {
        val q = quote {
          e.filter(_.i == 999).delete
        }
        testContext.run(q).string mustEqual
          "DELETE FROM test_entity WHERE field_i = 999"
      }
      "returning" - {
        "alias" in {
          val q = quote {
            e.insert(lift(TestEntity("s", 1, 1L, None))).returning(_.i)
          }
          val mirror = testContext.run(q.dynamic)
          mirror.returningColumn mustEqual "field_i"
        }
      }
    }
    "flatMap" - {
      "body" in {
        val q = quote {
          e.flatMap(t => qr2.filter(u => u.s == t.s))
        }
        testContext.run(q).string mustEqual
          "SELECT u.s, u.i, u.l, u.o FROM test_entity t, TestEntity2 u WHERE u.s = t.field_s"
      }
      "transitive" in {
        val q = quote {
          e.flatMap(t => qr2.map(u => t)).map(t => t.s)
        }
        testContext.run(q).string mustEqual
          "SELECT t.field_s FROM test_entity t, TestEntity2 u"
      }
    }
    "map" - {
      "body" in {
        val q = quote {
          e.map(t => (t.i, t.l))
        }
        testContext.run(q).string mustEqual
          "SELECT t.field_i, t.l FROM test_entity t"
      }
      "transitive" in {
        val q = quote {
          e.map(t => t).filter(t => t.i == 1)
        }
        testContext.run(q).string mustEqual
          "SELECT t.field_s, t.field_i, t.l, t.o FROM test_entity t WHERE t.field_i = 1"
      }
    }
    "filter" - {
      "body" in {
        val q = quote {
          e.filter(t => t.i == 1)
        }
        testContext.run(q).string mustEqual
          "SELECT t.field_s, t.field_i, t.l, t.o FROM test_entity t WHERE t.field_i = 1"
      }
      "transitive" in {
        val q = quote {
          e.filter(t => t.l == 1).map(t => t.s)
        }
        testContext.run(q).string mustEqual
          "SELECT t.field_s FROM test_entity t WHERE t.l = 1"
      }
    }
    "sortBy" - {
      "body" in {
        val q = quote {
          e.sortBy(t => t.i)
        }
        testContext.run(q).string mustEqual
          "SELECT t.field_s, t.field_i, t.l, t.o FROM test_entity t ORDER BY t.field_i ASC NULLS FIRST"
      }
      "transitive" in {
        val q = quote {
          e.sortBy(t => t.l).map(t => t.s)
        }
        testContext.run(q).string mustEqual
          "SELECT t.field_s FROM test_entity t ORDER BY t.l ASC NULLS FIRST"
      }
    }
    "take" - {
      "body" in {
        val q = quote {
          e.take(1)
        }
        testContext.run(q).string mustEqual
          "SELECT x.field_s, x.field_i, x.l, x.o FROM test_entity x LIMIT 1"
      }
      "transitive" in {
        val q = quote {
          e.take(1).map(t => t.s)
        }
        testContext.run(q).string mustEqual
          "SELECT t.field_s FROM test_entity t LIMIT 1"
      }
    }
    "drop" - {
      "body" in {
        val q = quote {
          e.drop(1)
        }
        testContext.run(q).string mustEqual
          "SELECT x.field_s, x.field_i, x.l, x.o FROM test_entity x OFFSET 1"
      }
      "transitive" in {
        val q = quote {
          e.drop(1).map(t => t.s)
        }
        testContext.run(q).string mustEqual
          "SELECT t.field_s FROM test_entity t OFFSET 1"
      }
    }
    "distinct" - {
      "body" in {
        val q = quote {
          e.distinct
        }
        testContext.run(q).string mustEqual
          "SELECT DISTINCT x.* FROM test_entity x"
      }
      "transitive" in {
        val q = quote {
          e.distinct.map(t => t.s)
        }
        testContext.run(q).string mustEqual
          "SELECT t.field_s FROM (SELECT DISTINCT x.field_s FROM test_entity x) t"
      }
    }

    "join" - {
      "both sides" in {
        val q = quote {
          e.leftJoin(e).on((a, b) => a.s == b.s).map(t => (t._1.s, t._2.map(_.s)))
        }
        testContext.run(q).string mustEqual
          "SELECT a.field_s, b.field_s FROM test_entity a LEFT JOIN test_entity b ON a.field_s = b.field_s"
      }
      "inner" in {
        val q = quote {
          e.join(f).on((a, b) => a.s == b.s).map(t => t._1.s)
        }
        testContext.run(q).string mustEqual
          "SELECT a.field_s FROM test_entity a INNER JOIN (SELECT t.s FROM TestEntity t WHERE t.i = 1) t ON a.field_s = t.s"
      }
      "left" in {
        val q = quote {
          e.leftJoin(f).on((a, b) => a.s == b.s).map(t => t._1.s)
        }
        testContext.run(q).string mustEqual
          "SELECT a.field_s FROM test_entity a LEFT JOIN (SELECT t.s FROM TestEntity t WHERE t.i = 1) t ON a.field_s = t.s"
      }
      "right" in {
        val q = quote {
          f.rightJoin(e).on((a, b) => a.s == b.s).map(t => t._2.s)
        }
        testContext.run(q).string mustEqual
          "SELECT b.field_s FROM (SELECT t.s FROM TestEntity t WHERE t.i = 1) t RIGHT JOIN test_entity b ON t.s = b.field_s"
      }
    }
  }

}
