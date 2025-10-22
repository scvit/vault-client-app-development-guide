package com.example.vaulttomcat.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * 테스트 Servlet
 */
public class TestServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    response.setContentType("text/html; charset=UTF-8");
    PrintWriter out = response.getWriter();

    out.println("<html>");
    out.println("<head><title>Test Servlet</title></head>");
    out.println("<body>");
    out.println("<h1>Test Servlet 작동 중!</h1>");
    out.println("<p>현재 시간: " + new java.util.Date() + "</p>");
    out.println("</body>");
    out.println("</html>");
  }
}
