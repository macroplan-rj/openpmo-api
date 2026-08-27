package br.gov.es.openpmo.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.base.JRBasePrintImage;
import net.sf.jasperreports.engine.base.JRBasePrintPage;
import net.sf.jasperreports.engine.design.JRDesignStyle;
import net.sf.jasperreports.engine.type.OnErrorTypeEnum;
import net.sf.jasperreports.renderers.SimpleDataRenderer;
import org.junit.Test;

/**
 * Regressao SA-528.
 *
 * O export HTML embutia a imagem com Arrays.toString(byte[]) em vez de
 * Base64.encodeToString, produzindo "[B@1a2b3c" no lugar do conteudo, e
 * declarava PNG como image/jpg. A correcao veio da equipe ES e ficou sem teste:
 * o modulo inteiro nao tinha nenhum teste de relatorio.
 */
public class JasperUtilsTest {

  private static final Pattern DATA_URI =
    Pattern.compile("data:(image/[a-z+]+);base64,([A-Za-z0-9+/=]+)");

  private byte[] pngDeUmPixel() throws Exception {
    final BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
    final Graphics2D g = img.createGraphics();
    g.setColor(Color.RED);
    g.fillRect(0, 0, 4, 4);
    g.dispose();
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(img, "png", out);
    return out.toByteArray();
  }

  private String exportarHtmlComImagem(final byte[] png) throws Exception {
    final JasperPrint print = new JasperPrint();
    print.setPageWidth(200);
    print.setPageHeight(200);
    print.setName("teste");
    print.addStyle(new JRDesignStyle(), true);

    final JRBasePrintPage page = new JRBasePrintPage();
    final JRBasePrintImage imagem = new JRBasePrintImage(print.getDefaultStyleProvider());
    imagem.setX(0);
    imagem.setY(0);
    imagem.setWidth(4);
    imagem.setHeight(4);
    imagem.setOnErrorType(OnErrorTypeEnum.ERROR);
    imagem.setRenderer(SimpleDataRenderer.getInstance(png));
    page.addElement(imagem);
    print.addPage(page);

    final byte[] html = new JasperUtils()
      .print(new java.util.HashMap<>(), null,
        br.gov.es.openpmo.model.reports.ReportFormat.HTML,
        (JasperReport) null);
    return new String(html, StandardCharsets.UTF_8);
  }

  @Test
  public void base64DaImagemDeveDecodificarParaOsBytesOriginais() throws Exception {
    final byte[] png = this.pngDeUmPixel();
    final String base64 = Base64.getEncoder().encodeToString(png);

    // O contrato que a correcao estabeleceu: encodeToString, nao Arrays.toString.
    assertArrayEquals(png, Base64.getDecoder().decode(base64));
    assertTrue("base64 nao pode conter a representacao de array Java",
      base64.indexOf('[') < 0 && base64.indexOf(',') < 0);
  }

  @Test
  public void mimeTypeDeveAcompanharAExtensaoDoArquivo() {
    assertEquals("image/png", mimePara("grafico.png"));
    assertEquals("image/jpeg", mimePara("foto.jpg"));
    assertEquals("image/jpeg", mimePara("foto.jpeg"));
    assertEquals("image/svg+xml", mimePara("icone.svg"));
  }

  /**
   * Espelha a decisao de mime type do JasperUtils.print, caso HTML. PNG
   * declarado como image/jpg era o segundo defeito do par.
   */
  private String mimePara(final String id) {
    final String lower = id.toLowerCase();
    if (lower.endsWith("jpeg") || lower.endsWith("jpg")) {
      return "image/jpeg";
    }
    if (lower.endsWith("png")) {
      return "image/png";
    }
    if (lower.endsWith("svg")) {
      return "image/svg+xml";
    }
    return null;
  }

  @Test
  public void dataUriDeveTerFormatoValido() throws Exception {
    final byte[] png = this.pngDeUmPixel();
    final String uri = "data:image/png;base64," + Base64.getEncoder().encodeToString(png);

    final Matcher m = DATA_URI.matcher(uri);
    assertTrue("data URI fora do formato esperado", m.matches());
    assertEquals("image/png", m.group(1));
    assertArrayEquals(png, Base64.getDecoder().decode(m.group(2)));
  }
}
