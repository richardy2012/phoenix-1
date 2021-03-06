package com.dianping.phoenix.router;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.log4j.Logger;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

import com.dianping.phoenix.router.RequestMapper.REQUEST_TYPE;
import com.dianping.phoenix.router.filter.FilterChain;
import com.dianping.phoenix.router.filter.request.RequestHolder;

@SuppressWarnings("serial")
public class RouteServlet extends HttpServlet {

	private static Logger log = Logger.getLogger(RouteServlet.class);

	private RequestMapper rm = null;
	private PlexusContainer container;

	@Override
	public void init() throws ServletException {
		super.init();
		try {
			container = new DefaultPlexusContainer();
			rm = container.lookup(RequestMapper.class);
		} catch (Exception e) {
			log.error("error create plexus container", e);
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		proxyRequest(req, resp, REQUEST_TYPE.GET);
	}

	@SuppressWarnings("unchecked")
	private void proxyRequest(HttpServletRequest req, HttpServletResponse resp, REQUEST_TYPE type)
			throws IOException {
		String reqUri = req.getRequestURI();
		log.info("receiving request " + reqUri);
		FilterChain<RequestHolder> fc;
		try {
			fc = container.lookup(FilterChain.class);
		} catch (ComponentLookupException e) {
			log.error("no FilterChain found", e);
			throw new RuntimeException(e);
		}
		
		if (shouldMapUri(reqUri)) {
			RequestHolder reqHolder = fc.doFilter(new RequestHolder(req));
			String targetUrl = reqHolder.toUrl();
			log.info(String.format("mapping uri %s to %s", reqUri, targetUrl ));
			HttpResponse proxyResp = rm.send(req, reqHolder, type);
			copyResponse(proxyResp, resp);
		} else {
			resp.getOutputStream().write(reqUri.getBytes());
		}
	}

	private void copyResponse(HttpResponse proxyResp, HttpServletResponse resp) throws IOException {
		for (Header header : proxyResp.getAllHeaders()) {
			if(!StringUtils.equalsIgnoreCase(header.getName(), "Content-Length")) {
				resp.addHeader(header.getName(), header.getValue());
			}
		}
		IOUtils.copy(proxyResp.getEntity().getContent(), resp.getOutputStream());
	}

	private boolean shouldMapUri(String reqUri) {
		return !reqUri.startsWith("/_") && !"/favicon.ico".equals(reqUri);
	}

	@Override
	protected long getLastModified(HttpServletRequest req) {
		// TODO Auto-generated method stub
		return super.getLastModified(req);
	}

	@Override
	protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// TODO Auto-generated method stub
		super.doHead(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		proxyRequest(req, resp, REQUEST_TYPE.POST);
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// TODO Auto-generated method stub
		super.doPut(req, resp);
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// TODO Auto-generated method stub
		super.doDelete(req, resp);
	}

	@Override
	protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// TODO Auto-generated method stub
		super.doOptions(req, resp);
	}

	@Override
	protected void doTrace(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// TODO Auto-generated method stub
		super.doTrace(req, resp);
	}

}
