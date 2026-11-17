package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * The layouts key: on every bundled layout and pad, in the same place (right of Ctrl, or first on
 * the bottom row of the 123+ and πλ∇¬ pads), its tap going to the layout's main destination and
 * each corner always to the same pad: up-left abc+, up-right 123+, down-left #+=, down-right πλ∇¬.
 * A corner never repeats the tap, and the corner of the layout itself stays empty.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28)
public class LayoutSwitchKeyTest
{
  private static final float EPS = 1e-4f;
  private static final int C = 0, NW = 1, NE = 2, SW = 3, SE = 4;

  @Test
  public void minimalLayout()
  {
    KeyboardData kb = load(R.xml.termux_launcher_minimal);
    assertEquals(12f, kb.keysWidth, EPS);
    assertSwitchKey(rightOfCtrl(kb), "switch_full", null, "switch_numeric", "switch_symbols", "switch_greekmath");
  }

  @Test
  public void minimalSpaceBarSlidesTheCursorLikeTheOthers()
  {
    KeyboardData.Key space = rightOfCtrl(load(R.xml.termux_launcher_minimal));
    for (KeyboardData.Row row : load(R.xml.termux_launcher_minimal).rows)
      for (KeyboardData.Key key : row.keys)
        if (KeyValue.getKeyByName("space").equals(key.getKeyValue(C)))
          space = key;
    assertValue("w", "cursor_left", space.getKeyValue(5));
    assertValue("e", "cursor_right", space.getKeyValue(6));
    KeyboardData.Key qwertySpace = null;
    for (KeyboardData.Row row : load(R.xml.termux_launcher_qwerty).rows)
      for (KeyboardData.Key key : row.keys)
        if (KeyValue.getKeyByName("space").equals(key.getKeyValue(C)))
          qwertySpace = key;
    // The sessions move to the lower corners, where the QWERTY space bar has them.
    assertEquals(qwertySpace.getKeyValue(SW), space.getKeyValue(SW));
    assertEquals(qwertySpace.getKeyValue(SE), space.getKeyValue(SE));
  }

  @Test
  public void qwertyLayout()
  {
    KeyboardData kb = load(R.xml.termux_launcher_qwerty);
    assertEquals(10f, kb.keysWidth, EPS);
    assertSwitchKey(rightOfCtrl(kb), "switch_full", null, "switch_numeric", "switch_symbols", "switch_greekmath");
  }

  @Test
  public void fullQwertyPad()
  {
    KeyboardData kb = load(R.xml.full_qwerty);
    assertEquals(10f, kb.keysWidth, EPS);
    assertSwitchKey(rightOfCtrl(kb), "switch_text", null, "switch_numeric", "switch_symbols", "switch_greekmath");
  }

  @Test
  public void symbolsPad()
  {
    KeyboardData kb = load(R.xml.symbols);
    assertEquals(10f, kb.keysWidth, EPS);
    assertSwitchKey(rightOfCtrl(kb), "switch_text", "switch_full", "switch_numeric", null, "switch_greekmath");
  }

  @Test
  public void numericPad()
  {
    KeyboardData kb = load(R.xml.numeric);
    assertEquals(6f, kb.keysWidth, EPS);
    assertSwitchKey(firstOfBottomRow(kb), "switch_text", "switch_full", null, "switch_symbols", "switch_greekmath");
    // Ctrl, which rode the old ABC key, takes the place of the πλ∇¬ key.
    assertNotNull(keyWithCentre(kb, "ctrl"));
    assertEquals(null, keyWithCentre(kb, "switch_greekmath"));
  }

  @Test
  public void greekMathPad()
  {
    KeyboardData kb = load(R.xml.greekmath);
    assertEquals(10f, kb.keysWidth, EPS);
    assertSwitchKey(firstOfBottomRow(kb), "switch_text", "switch_full", "switch_numeric", "switch_symbols", null);
    // Esc and Tab, which rode the old ABC and 123+ keys, take the place of the 123+ key.
    KeyboardData.Key esc = keyWithCentre(kb, "esc");
    assertNotNull(esc);
    assertEquals(KeyValue.getKeyByName("tab"), esc.getKeyValue(NE));
    assertEquals(null, keyWithCentre(kb, "switch_numeric"));
  }

  private static KeyboardData load(int id)
  {
    return KeyboardData.load(
        org.robolectric.RuntimeEnvironment.getApplication().getResources(), id);
  }

  private static KeyboardData.Key rightOfCtrl(KeyboardData kb)
  {
    List<KeyboardData.Key> keys = kb.rows.get(kb.rows.size() - 1).keys;
    for (int i = 0; i + 1 < keys.size(); i++)
      if (KeyValue.getKeyByName("ctrl").equals(keys.get(i).getKeyValue(C)))
        return keys.get(i + 1);
    fail("no Ctrl on the bottom row");
    return null;
  }

  private static final int[] ALL = {R.xml.termux_launcher_minimal, R.xml.termux_launcher_qwerty,
      R.xml.full_qwerty, R.xml.symbols, R.xml.numeric, R.xml.greekmath};

  /** Ctrl carries fn, Alt, Super and Settings on its corners, the same on every layout. */
  @Test
  public void everyCtrlCarriesTheSameCorners()
  {
    for (int id : ALL)
    {
      KeyboardData.Key ctrl = keyWithCentre(load(id), "ctrl");
      assertNotNull("a Ctrl key in layout " + id, ctrl);
      assertSwitchKey(ctrl, "ctrl", "fn", "alt", "meta", "config");
    }
  }

  /** No Alt key on any bottom row; where there is a Shift, Alt has its place and it rides up-right. */
  @Test
  public void altTakesTheShiftPlaceAndLeavesTheBottomRow()
  {
    for (int id : ALL)
    {
      KeyboardData kb = load(id);
      for (KeyboardData.Key key : kb.rows.get(kb.rows.size() - 1).keys)
        assertTrue("no Alt on the bottom row of " + id,
            !KeyValue.getKeyByName("alt").equals(key.getKeyValue(C)));
      assertEquals("no Shift key of its own in " + id, null, keyWithCentre(kb, "shift"));
    }
    for (int id : new int[] {R.xml.termux_launcher_qwerty, R.xml.full_qwerty, R.xml.numeric,
        R.xml.greekmath})
    {
      KeyboardData.Key alt = keyWithCentre(load(id), "alt");
      assertNotNull("an Alt key in " + id, alt);
      assertEquals(KeyValue.getKeyByName("shift"), alt.getKeyValue(NE));
    }
  }

  /** The IME action key typed a second Enter in a terminal; = has its place. */
  @Test
  public void numericPadHasOneEnter()
  {
    KeyboardData kb = load(R.xml.numeric);
    assertEquals(null, keyWithCentre(kb, "action"));
    assertNotNull(keyWithCentre(kb, "enter"));
  }

  private static KeyboardData.Key firstOfBottomRow(KeyboardData kb)
  {
    return kb.rows.get(kb.rows.size() - 1).keys.get(0);
  }

  private static KeyboardData.Key keyWithCentre(KeyboardData kb, String name)
  {
    KeyValue wanted = KeyValue.getKeyByName(name);
    for (KeyboardData.Row row : kb.rows)
      for (KeyboardData.Key key : row.keys)
        if (wanted.equals(key.getKeyValue(C)))
          return key;
    return null;
  }

  private static void assertSwitchKey(KeyboardData.Key key, String c, String nw, String ne,
      String sw, String se)
  {
    assertValue("centre", c, key.getKeyValue(C));
    assertValue("nw", nw, key.getKeyValue(NW));
    assertValue("ne", ne, key.getKeyValue(NE));
    assertValue("sw", sw, key.getKeyValue(SW));
    assertValue("se", se, key.getKeyValue(SE));
  }

  private static void assertValue(String where, String name, KeyValue actual)
  {
    assertEquals(where, name == null ? null : KeyValue.getKeyByName(name), actual);
  }
}
