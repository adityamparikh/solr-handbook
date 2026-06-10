package solrbook.indexing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AtomicOpTest {

  @Test
  void modifierMapsUseTheSixSupportedModifiers() {
    assertEquals("v", AtomicOp.set("v").get("set"));
    assertEquals(1, AtomicOp.inc(1).get("inc"));
    assertEquals("x", AtomicOp.add("x").get("add"));
    assertEquals("x", AtomicOp.addDistinct("x").get("add-distinct"));
    assertEquals("x", AtomicOp.remove("x").get("remove"));
    assertEquals("a.*", AtomicOp.removeRegex("a.*").get("removeregex"));
  }

  @Test
  void setNullRemovesTheField() {
    assertTrue(AtomicOp.setNull().containsKey("set"));
    assertNull(AtomicOp.setNull().get("set"));
  }

  @Test
  void everyOpIsASingleEntryMap() {
    assertEquals(1, AtomicOp.set("v").size());
    assertEquals(1, AtomicOp.inc(-2).size());
    assertEquals(1, AtomicOp.addDistinct("x").size());
  }
}
