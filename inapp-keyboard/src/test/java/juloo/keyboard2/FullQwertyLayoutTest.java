package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * The abc+ pad (full_qwerty.xml) in ten columns: every row at most ten units wide, so the letters
 * get the room the old thirteen-column pad gave to digits and side columns, with nothing lost on
 * the way to the letters' corners.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28)
public class FullQwertyLayoutTest
{
  private static final float EPS = 1e-4f;
  private static final int C = 0, NW = 1, NE = 2, SW = 3, SE = 4;

  @Test
  public void everyRowIsTenUnitsWide()
  {
    KeyboardData kb = pad();
    assertEquals(10f, kb.keysWidth, EPS);
    for (KeyboardData.Row row : kb.rows)
      assertTrue("row of " + row.keysWidth + " units", row.keysWidth <= 10f + EPS);
  }

  @Test
  public void everyOldSymbolIsStillTypable()
  {
    Set<String> typed = new HashSet<>();
    Set<Integer> events = new HashSet<>();
    for (KeyboardData.Row row : pad().rows)
      for (KeyboardData.Key key : row.keys)
        for (int i = 0; i < 9; i++)
        {
          KeyValue v = key.getKeyValue(i);
          if (v == null)
            continue;
          if (v.getKind() == KeyValue.Kind.Char)
            typed.add(v.getString());
          if (v.getKind() == KeyValue.Kind.Keyevent)
            events.add(v.getKeyevent());
        }
    for (String s : new String[]{ "`", "~", "!", "@", "#", "$", "%", "^", "&", "*", "(", ")",
        "-", "_", "=", "+", "[", "]", "{", "}", ";", ":", "'", "\"", "\\", "|", ",", "<", ".",
        ">", "/", "?" })
      assertTrue("missing " + s, typed.contains(s));
    for (int code = 131; code <= 142; code++)
      assertTrue("missing F" + (code - 130), events.contains(code));
    KeyboardData kb = pad();
    assertTrue(kb.getKeys().containsKey(KeyValue.getKeyByName("tab")));
    assertTrue(kb.getKeys().containsKey(KeyValue.getKeyByName("esc")));
  }

  @Test
  public void cornersFollowTheAgreedOrder()
  {
    KeyboardData kb = pad();
    corners(kb, "o", "[", "{");
    corners(kb, "p", "]", "}");
    corners(kb, "j", ";", ":");
    corners(kb, "k", "'", "\"");
    corners(kb, "l", "\\", "|");
    corners(kb, "b", ",", "<");
    corners(kb, "n", ".", ">");
    corners(kb, "m", "/", "?");
    corners(kb, "!", "`", "~");
    corners(kb, "(", "=", "+");
    corners(kb, ")", "-", "_");
    assertEquals(KeyValue.getKeyByName("tab"), key(kb, "q").getKeyValue(SE));
    assertEquals(KeyValue.getKeyByName("esc"), key(kb, "a").getKeyValue(SE));
  }

  @Test
  public void noDigitIsAKeyOfItsOwn()
  {
    for (KeyboardData.Row row : pad().rows)
      for (KeyboardData.Key key : row.keys)
      {
        KeyValue v = key.getKeyValue(C);
        assertFalse("digit key " + v.getString(),
            v.getKind() == KeyValue.Kind.Char && Character.isDigit(v.getChar()));
      }
  }

  @Test
  public void altStillLeadsBack()
  {
    KeyboardData.Key alt = null;
    for (KeyboardData.Row row : pad().rows)
      for (KeyboardData.Key key : row.keys)
        if (KeyValue.getKeyByName("alt").equals(key.getKeyValue(C)))
          alt = key;
    assertNotNull("an Alt key", alt);
    assertEquals(KeyValue.getKeyByName("switch_text"), alt.getKeyValue(SW));
  }

  @Test
  public void fullPadPartsInTheThumbGroups()
  {
    KeyboardData kb = pad();
    KeyboardData split = LayoutModifier.split(kb, LayoutModifier.gapUnits(kb, 0.25f));
    assertRun(split.rows.get(1), "t", "y");
    assertRun(split.rows.get(2), "g", "h");
    assertRun(split.rows.get(3), "v", "b");
  }

  private static KeyboardData pad()
  {
    android.content.res.Resources resources =
        org.robolectric.RuntimeEnvironment.getApplication().getResources();
    return KeyboardData.load(resources, R.xml.full_qwerty);
  }

  private static KeyboardData.Key key(KeyboardData kb, String c)
  {
    for (KeyboardData.Row row : kb.rows)
      for (KeyboardData.Key key : row.keys)
      {
        KeyValue v = key.getKeyValue(C);
        if (v != null && v.getKind() == KeyValue.Kind.Char && v.getString().equals(c))
          return key;
      }
    throw new AssertionError("no key " + c);
  }

  /** The key {@code c} carries {@code sw} on its down-left corner and {@code nw} on its up-left. */
  private static void corners(KeyboardData kb, String c, String sw, String nw)
  {
    KeyboardData.Key key = key(kb, c);
    assertEquals(c + " sw", sw, string(key.getKeyValue(SW)));
    assertEquals(c + " nw", nw, string(key.getKeyValue(NW)));
  }

  private static String string(KeyValue v)
  {
    return v == null ? null : v.getString();
  }

  private static void assertRun(KeyboardData.Row row, String lastLeft, String firstRight)
  {
    int at = -1;
    float widest = 0f;
    for (int i = 1; i < row.keys.size(); i++)
      if (row.keys.get(i).shift > widest)
      {
        widest = row.keys.get(i).shift;
        at = i;
      }
    assertTrue("the row parts", at > 0 && at < row.keys.size());
    assertEquals(lastLeft, row.keys.get(at - 1).getKeyValue(C).getString());
    assertEquals(firstRight, row.keys.get(at).getKeyValue(C).getString());
  }
}
