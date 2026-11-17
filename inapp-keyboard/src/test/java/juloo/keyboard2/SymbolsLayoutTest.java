package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * The #+= pad (symbols.xml): Gboard's symbols page without its digits, ten columns wide, every
 * ASCII symbol on a key of its own and the second Gboard page on the corners.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28)
public class SymbolsLayoutTest
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
  public void splitHalvesAreFiveUnitsEach()
  {
    // A row parted off-centre widens both halves and shrinks every key in split mode.
    assertEquals(5f, SplitLayout.halfUnits(pad()), EPS);
  }

  @Test
  public void everyAsciiSymbolIsAKeyOfItsOwn()
  {
    Set<String> centres = new HashSet<>();
    for (KeyboardData.Row row : pad().rows)
      for (KeyboardData.Key key : row.keys)
      {
        KeyValue v = key.getKeyValue(C);
        if (v != null && v.getKind() == KeyValue.Kind.Char)
          centres.add(v.getString());
      }
    // { and } are the only ASCII symbols on corners.
    for (String s : new String[]{ "`", "~", "!", "@", "#", "$", "%", "^", "&", "*", "(", ")",
        "-", "_", "=", "+", "[", "]", ";", ":", "'", "\"", "\\", "|", ",", "<", ".", ">", "/",
        "?" })
      assertTrue("no key for " + s, centres.contains(s));
  }

  @Test
  public void secondariesFollowTheAgreedOrder()
  {
    KeyboardData kb = pad();
    corners(kb, "@", "©", "®", "™", null);
    corners(kb, "#", "§", null, null, null);
    corners(kb, "$", "€", "£", "¥", "¢");
    corners(kb, "-", "•", null, null, null);
    corners(kb, "+", "π", "Δ", null, null);
    corners(kb, "*", "×", "÷", null, null);
    corners(kb, "!", "✓", null, null, null);
    corners(kb, "^", "√", "°", null, null);
    corners(kb, "[", null, "{", null, null);
    corners(kb, "]", null, "}", null, null);
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

  private static KeyboardData pad()
  {
    android.content.res.Resources resources =
        org.robolectric.RuntimeEnvironment.getApplication().getResources();
    return KeyboardData.load(resources, R.xml.symbols);
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

  private static void corners(KeyboardData kb, String c, String sw, String nw, String ne, String se)
  {
    KeyboardData.Key key = key(kb, c);
    assertEquals(c + " sw", sw, string(key.getKeyValue(SW)));
    assertEquals(c + " nw", nw, string(key.getKeyValue(NW)));
    assertEquals(c + " ne", ne, string(key.getKeyValue(NE)));
    assertEquals(c + " se", se, string(key.getKeyValue(SE)));
  }

  private static String string(KeyValue v)
  {
    return v == null ? null : v.getString();
  }
}
