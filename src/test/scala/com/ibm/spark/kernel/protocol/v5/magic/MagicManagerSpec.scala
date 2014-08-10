package com.ibm.spark.kernel.protocol.v5.magic

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import com.ibm.spark.interpreter.{ExecuteOutput, ExecuteError}
import com.ibm.spark.magic.MagicLoader
import com.typesafe.config.ConfigFactory
import org.mockito.Matchers.{eq => mockEq, _}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSpecLike, Matchers}

import scala.concurrent.duration._

object MagicManagerSpec {
  val config = """
    akka {
      loglevel = "WARNING"
    }"""
}

class FakeClass {
  def execute(code: String, isCell: Boolean) = "MY TEST VALUE"
}

class BadFakeClass {
  def execute(code: String, isCell: Boolean) =
    throw new Throwable("EXPLOSION")
}

class MagicManagerSpec extends TestKit(
  ActorSystem(
    "MagicManagerSpec",
    ConfigFactory.parseString(MagicManagerSpec.config)
  )
) with ImplicitSender with FunSpecLike with Matchers with MockitoSugar {
  describe("MagicManager") {
    describe("#receive") {
      describe("with message type ValidateMagicMessage") {
        it("should return false if the code does not parse as magic") {
          val mockMagicLoader = mock[MagicLoader]
          val magicManager =
            system.actorOf(Props(classOf[MagicManager], mockMagicLoader))

          magicManager ! ValidateMagicMessage("notAMagic")

          expectMsg(false)
        }

        it("should return true if code parses as line magic") {
          val mockMagicLoader = mock[MagicLoader]
          val magicManager =
            system.actorOf(Props(classOf[MagicManager], mockMagicLoader))

          magicManager ! ValidateMagicMessage("%lineMagic asdfasdf")

          expectMsg(true)
        }

        it("should return true if code parses as cell magic") {
          val mockMagicLoader = mock[MagicLoader]
          val magicManager =
            system.actorOf(Props(classOf[MagicManager], mockMagicLoader))

          magicManager ! ValidateMagicMessage("%%cellMagic asdflj\nasdf\n")

          expectMsg(true)
        }
      }

      describe("with message type ExecuteMagicMessage") {
        it("should return an error if the magic requested is not defined") {
          val fakeMagicName = "myMagic"
          val mockMagicLoader = mock[MagicLoader]
          doReturn(false).when(mockMagicLoader).hasMagic(anyString())
          val magicManager =
            system.actorOf(Props(classOf[MagicManager], mockMagicLoader))

          magicManager ! ExecuteMagicMessage("%%" + fakeMagicName)

          // Expect magic to not exist
          expectMsg(Right(ExecuteError(
            "Missing Magic",
            s"Magic $fakeMagicName does not exist!",
            List()
          )))
        }

        it("should evaluate the magic if it exists and return the error if it fails") {
          val fakeMagicName = "myBadMagic"
          val myMagicLoader = new MagicLoader() {
            override def findClass(name: String): Class[_] =
              new BadFakeClass().getClass
          }
          val magicManager =
            system.actorOf(Props(classOf[MagicManager], myMagicLoader))

          magicManager ! ExecuteMagicMessage("%%" + fakeMagicName)

          val result =
            receiveOne(5.seconds)
              .asInstanceOf[Either[ExecuteOutput, ExecuteError]]

          result.right.get shouldBe an [ExecuteError]
        }

        it("should evaluate the magic if it exists and return the output if it succeeds") {
          val fakeMagicName = "myMagic"
          val myMagicLoader = new MagicLoader() {
            override def findClass(name: String): Class[_] =
              new FakeClass().getClass
          }
          val magicManager =
            system.actorOf(Props(classOf[MagicManager], myMagicLoader))

          magicManager ! ExecuteMagicMessage("%%" + fakeMagicName)

          expectMsg(Left("MY TEST VALUE"))
        }
      }
    }
  }
}