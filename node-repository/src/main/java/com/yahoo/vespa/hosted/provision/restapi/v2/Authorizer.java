// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * Authorizer for the node-repository and orchestrator REST APIs. This contains the authorization rules for all API
 * paths.
 *
 * Ideally, the authorization rules for orchestrator APIs should live in the orchestrator module. However, the node
 * repository is required to make decisions in some cases, which is not accessible in the orchestrator module.
 *
 * @author mpolden
 */
public class Authorizer implements BiPredicate<Principal, URI> {

    private final SystemName system;
    private final NodeRepository nodeRepository;

    public Authorizer(SystemName system, NodeRepository nodeRepository) {
        this.system = system;
        this.nodeRepository = nodeRepository;
    }

    /** Returns whether principal is authorized to access given URI */
    @Override
    public boolean test(Principal principal, URI uri) {
        // Trusted services can access everything
        if (principal.getName().equals(trustedService())) {
            return true;
        }

        // Nodes can only access its own resources
        if (canAccess(hostnamesFrom(uri), principal)) {
            return true;
        }

        return false;
    }

    /** Returns whether principal can access node identified by hostname */
    private boolean canAccess(String hostname, Principal principal) {
        // Ignore potential path traversal. Node repository happily passes arguments unsanitized all the way down to
        // curator...
        if (hostname.chars().allMatch(c -> c == '.')) {
            return false;
        }

        // Node can always access itself
        if (principal.getName().equals(hostname)) {
            return true;
        }

        // Parent node can access its children
        return nodeRepository.getNode(hostname)
                             .flatMap(Node::parentHostname)
                             .map(parentHostname -> principal.getName().equals(parentHostname))
                             .orElse(false);
    }

    /** Returns whether principal can access all nodes identified by given hostnames */
    private boolean canAccess(List<String> hostnames, Principal principal) {
        return !hostnames.isEmpty() && hostnames.stream().allMatch(hostname -> canAccess(hostname, principal));
    }

    /** Trusted service name for this system */
    private String trustedService() {
        if (system != SystemName.main) {
            return "vespa.vespa." + system.name() + ".hosting";
        }
        return "vespa.vespa.hosting";
    }

    /** Returns hostnames contained in query parameters of given URI */
    private static List<String> hostnamesFromQuery(URI uri) {
        return URLEncodedUtils.parse(uri, StandardCharsets.UTF_8.name())
                              .stream()
                              .filter(pair -> "hostname".equals(pair.getName()) ||
                                              "parentHost".equals(pair.getName()))
                              .map(NameValuePair::getValue)
                              .filter(hostname -> !hostname.isEmpty())
                              .collect(Collectors.toList());
    }

    /** Returns hostnames from a URI if any, e.g. /nodes/v2/node/node1.fqdn */
    private static List<String> hostnamesFrom(URI uri) {
        if (isChildOf("/nodes/v2/acl/", uri.getPath()) ||
            isChildOf("/nodes/v2/node/", uri.getPath()) ||
            isChildOf("/nodes/v2/state/", uri.getPath())) {
            return Collections.singletonList(lastChildOf(uri.getPath()));
        }
        if (isChildOf("/orchestrator/v1/hosts/", uri.getPath())) {
            return firstChildOf("/orchestrator/v1/hosts/", uri.getPath())
                    .map(Collections::singletonList)
                    .orElseGet(Collections::emptyList);
        }
        if (isChildOf("/orchestrator/v1/suspensions/hosts/", uri.getPath())) {
            List<String> hostnames = new ArrayList<>();
            hostnames.add(lastChildOf(uri.getPath()));
            hostnames.addAll(hostnamesFromQuery(uri));
            return hostnames;
        }
        if (isChildOf("/nodes/v2/command/", uri.getPath()) ||
            "/nodes/v2/node/".equals(uri.getPath())) {
            return hostnamesFromQuery(uri);
        }
        return Collections.emptyList();
    }

    /** Returns whether child is a sub-path of parent */
    private static boolean isChildOf(String parent, String child) {
        return child.startsWith(parent) && child.length() > parent.length();
    }

    /** Returns the first component of path relative to root */
    private static Optional<String> firstChildOf(String root, String path) {
        if (!isChildOf(root, path)) {
            return Optional.empty();
        }
        path = path.substring(root.length(), path.length());
        int firstSeparator = path.indexOf('/');
        if (firstSeparator == -1) {
            return Optional.of(path);
        }
        return Optional.of(path.substring(0, firstSeparator));
    }

    /** Returns the last component of the given path */
    private static String lastChildOf(String path) {
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        int lastSeparator = path.lastIndexOf("/");
        if (lastSeparator == - 1) {
            return path;
        }
        return path.substring(lastSeparator + 1, path.length());
    }

}