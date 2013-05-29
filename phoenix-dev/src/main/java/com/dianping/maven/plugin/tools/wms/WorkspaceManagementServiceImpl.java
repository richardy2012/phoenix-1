/**
 * Project: phoenix-maven-plugin
 * 
 * File Created at 2013-5-14
 * $Id$
 * 
 * Copyright 2010 dianping.com.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Dianping Company. ("Confidential Information").  You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with dianping.com.
 */
package com.dianping.maven.plugin.tools.wms;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.unidal.lookup.annotation.Inject;

import com.dianping.maven.plugin.tools.generator.dynamic.ContainerBizServerGenerator;
import com.dianping.maven.plugin.tools.generator.dynamic.ContainerPomXMLGenerator;
import com.dianping.maven.plugin.tools.generator.dynamic.ContainerWebXMLGenerator;
import com.dianping.maven.plugin.tools.generator.dynamic.WorkspaceEclipseBatGenerator;
import com.dianping.maven.plugin.tools.generator.dynamic.WorkspaceEclipseSHGenerator;
import com.dianping.maven.plugin.tools.generator.dynamic.WorkspacePomXMLGenerator;
import com.dianping.maven.plugin.tools.vcs.CodeRetrieveConfig;
import com.dianping.maven.plugin.tools.vcs.CodeRetrieverManager;
import com.dianping.maven.plugin.tools.vcs.GitCodeRetrieveConfig;
import com.dianping.maven.plugin.tools.vcs.SVNCodeRetrieveConfig;

/**
 * 
 * @author Leo Liang
 * 
 */
public class WorkspaceManagementServiceImpl implements WorkspaceManagementService {

    private final static String  LINE_SEPARATOR   = System.getProperty("line.separator");
    private final static String  CONTAINER_FOLDER = "phoenix-container";

    @Inject
    private RepositoryManager    repositoryManager;
    @Inject
    private CodeRetrieverManager codeRetrieverManager;

    public void setRepositoryManager(RepositoryManager repositoryManager) {
        this.repositoryManager = repositoryManager;
    }

    @Override
    public File create(WorkspaceContext context, OutputStream out) throws WorkspaceManagementException {
        if (context.getProjects() != null && context.getBaseDir() != null && out != null) {

            printContent("Generating phoenix workspace...", out);

            if (context.getBaseDir().exists() && context.isCleanFolder()) {
                try {
                    FileUtils.cleanDirectory(context.getBaseDir());
                } catch (IOException e) {
                    throw new WorkspaceManagementException(e);
                }
                printContent(String.format("Workspace folder(%s) cleared...", context.getBaseDir()), out);
            }

            if (!context.getBaseDir().exists()) {
                try {
                    FileUtils.forceMkdir(context.getBaseDir());
                } catch (IOException e) {
                    throw new WorkspaceManagementException(e);
                }
                printContent(String.format("Workspace folder(%s) created...", context.getBaseDir()), out);
            }

            for (String project : context.getProjects()) {
                Repository repository = repositoryManager.find(project);
                if (repository == null) {
                    printContent(String.format("Project(%s) not found...", project), out);
                }

                CodeRetrieveConfig codeRetrieveConfig = toCodeRetrieveConfig(repository, new File(context.getBaseDir(),
                        project).getAbsolutePath(), out);
                if (codeRetrieveConfig != null) {
                    printContent(
                            String.format("Checking out project %s(repo:%s)...", project, repository.getRepoUrl()), out);
                    codeRetrieverManager.getCodeRetriever(codeRetrieveConfig).retrieveCode();
                } else {
                    printContent(String.format("Project repository(%s) unknown...", project), out);
                }
            }

            printContent("Generating phoenix-container...", out);

            generateContainerProject(context, out);

            printContent("Generating phoenix-workspace pom...", out);

            generateWorkspacePom(context);

            printContent("Generating phoenix-workspace startup script...", out);

            generateWorkspaceScript(context);

            printContent("Generating ws folder...", out);

            File workspaceFolder = new File(context.getBaseDir(), "ws");

            if (!workspaceFolder.exists()) {
                try {
                    FileUtils.forceMkdir(workspaceFolder);
                } catch (IOException e) {
                    throw new WorkspaceManagementException(e);
                }

                printContent("Phoenix workspace generated...", out);
            }

            return new File(context.getBaseDir(), CONTAINER_FOLDER);

        } else {
            throw new WorkspaceManagementException("projects/basedir can not be null");
        }
    }

    private void generateWorkspaceScript(WorkspaceContext context) throws WorkspaceManagementException {
        try {
            WorkspaceEclipseBatGenerator workspaceEclipseBatGenerator = new WorkspaceEclipseBatGenerator();
            File eclipseBatFile = new File(context.getBaseDir(), "eclipse.bat");
            workspaceEclipseBatGenerator.generate(eclipseBatFile, null);
            eclipseBatFile.setExecutable(true);

            WorkspaceEclipseSHGenerator workspaceEclipseSHGenerator = new WorkspaceEclipseSHGenerator();
            File eclipseSHFile = new File(context.getBaseDir(), "eclipse.sh");
            workspaceEclipseSHGenerator.generate(eclipseSHFile, null);
            eclipseSHFile.setExecutable(true);
        } catch (Exception e) {
            throw new WorkspaceManagementException(e);
        }
    }

    private void generateWorkspacePom(WorkspaceContext context) throws WorkspaceManagementException {
        WorkspacePomXMLGenerator generator = new WorkspacePomXMLGenerator();
        try {
            generator.generate(new File(context.getBaseDir(), "pom.xml"), context.getProjects());
        } catch (Exception e) {
            throw new WorkspaceManagementException(e);
        }

    }

    private void generateContainerProject(WorkspaceContext context, OutputStream out)
            throws WorkspaceManagementException {
        File projectBase = new File(context.getBaseDir(), CONTAINER_FOLDER);
        File sourceFolder = new File(projectBase, "src/main/java");
        File resourceFolder = new File(projectBase, "src/main/resources");
        File webinfFolder = new File(projectBase, "src/main/webapp/WEB-INF");
        try {
            FileUtils.forceMkdir(sourceFolder);
            FileUtils.forceMkdir(resourceFolder);
            FileUtils.forceMkdir(webinfFolder);

            copyJar("byteman-2.1.2.jar", resourceFolder);
            copyJar("instrumentation-util-0.0.1.jar", resourceFolder);

            // clone gitconfig
            GitCodeRetrieveConfig gitConfig = new GitCodeRetrieveConfig(context.getGitConfigRepositoryUrl(), new File(
                    resourceFolder, "gitconf").getAbsolutePath(), out, context.getGitConfigRepositoryBranch());
            codeRetrieverManager.getCodeRetriever(gitConfig).retrieveCode();

            // web.xml
            ContainerWebXMLGenerator containerWebXMLGenerator = new ContainerWebXMLGenerator();
            containerWebXMLGenerator.generate(new File(webinfFolder, "web.xml"), null);

            // pom.xml
            ContainerPomXMLGenerator containerPomXMLGenerator = new ContainerPomXMLGenerator();
            Map<String, String> containerPomXMLGeneratorContext = new HashMap<String, String>();
            containerPomXMLGeneratorContext.put("phoenixRouterVersion", context.getPhoenixRouterVersion());
            containerPomXMLGenerator.generate(new File(projectBase, "pom.xml"), containerPomXMLGeneratorContext);

            // BizServer.java
            ContainerBizServerGenerator containerBizServerGenerator = new ContainerBizServerGenerator();
            containerBizServerGenerator.generate(
                    new File(sourceFolder, "com/dianping/phoenix/container/PhoenixServer.java"), null);

        } catch (Exception e) {
            throw new WorkspaceManagementException(e);
        }
    }

    private void copyJar(String fileName, File resourceFolder) throws FileNotFoundException, IOException {
        InputStream byteManStream = this.getClass().getResourceAsStream("/" + fileName);
        FileOutputStream byteManJar = new FileOutputStream(new File(resourceFolder, fileName));
        IOUtils.copy(byteManStream, byteManJar);
        IOUtils.closeQuietly(byteManStream);
        IOUtils.closeQuietly(byteManJar);
    }

    private void printContent(String content, OutputStream out) {

        try {
            out.write(("---------------------------------------------" + LINE_SEPARATOR).getBytes());
            out.write((content + LINE_SEPARATOR).getBytes());
            out.write(("---------------------------------------------" + LINE_SEPARATOR).getBytes());
        } catch (IOException e) {
            // ignore
        }
    }

    private CodeRetrieveConfig toCodeRetrieveConfig(Repository repository, String path, OutputStream out) {
        if (repository instanceof SvnRepository) {
            return new SVNCodeRetrieveConfig(repository.getRepoUrl(), path, out,
                    ((SvnRepository) repository).getRevision());
        } else if (repository instanceof GitRepository) {
            return new GitCodeRetrieveConfig(repository.getRepoUrl(), path, out,
                    ((GitRepository) repository).getBranch());
        } else {
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        PlexusContainer plexusContainer = new DefaultPlexusContainer();
        WorkspaceManagementServiceImpl wms = new WorkspaceManagementServiceImpl();
        wms.setRepositoryManager(plexusContainer.lookup(RepositoryManager.class));
        wms.codeRetrieverManager = plexusContainer.lookup(CodeRetrieverManager.class);
        wms.setRepositoryManager(new DummyRepositoryManager());
        WorkspaceContext context = new WorkspaceContext();
        List<String> projects = new ArrayList<String>();
        projects.add("shop-web");
        projects.add("shoplist-web");
        projects.add("user-web");
        projects.add("user-service");
        projects.add("user-base-service");
        context.setProjects(projects);
        context.setBaseDir(new File("/Users/leoleung/test"));
        context.setGitConfigRepositoryBranch("master");
        context.setGitConfigRepositoryUrl("http://code.dianpingoa.com/arch/phoenix-maven-config.git");
        wms.create(context, System.out);
    }
}
