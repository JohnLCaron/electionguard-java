package org.gradle.accessors.dm;

import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.plugin.use.PluginDependency;
import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.internal.catalog.AbstractExternalDependencyFactory;
import org.gradle.api.internal.catalog.DefaultVersionCatalog;
import java.util.Map;
import javax.inject.Inject;

/**
 * A catalog of dependencies accessible via the `libs` extension.
*/
@NonNullApi
public class LibrariesForLibs extends AbstractExternalDependencyFactory {

    private final AbstractExternalDependencyFactory owner = this;
    private final VersionAccessors vaccForVersionAccessors = new VersionAccessors(providers, config);
    private final BundleAccessors baccForBundleAccessors = new BundleAccessors(providers, config);
    private final PluginAccessors paccForPluginAccessors = new PluginAccessors(providers, config);

    @Inject
    public LibrariesForLibs(DefaultVersionCatalog config, ProviderFactory providers) {
        super(config, providers);
    }

        /**
         * Creates a dependency provider for bytesLib (at.favre.lib:bytes)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getBytesLib() { return create("bytesLib"); }

        /**
         * Creates a dependency provider for flogger (com.google.flogger:flogger)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getFlogger() { return create("flogger"); }

        /**
         * Creates a dependency provider for floggerBackend (com.google.flogger:flogger-system-backend)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getFloggerBackend() { return create("floggerBackend"); }

        /**
         * Creates a dependency provider for grpcBom (io.grpc:grpc-bom)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getGrpcBom() { return create("grpcBom"); }

        /**
         * Creates a dependency provider for grpcNettyShaded (io.grpc:grpc-netty-shaded)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getGrpcNettyShaded() { return create("grpcNettyShaded"); }

        /**
         * Creates a dependency provider for grpcProtobuf (io.grpc:grpc-protobuf)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getGrpcProtobuf() { return create("grpcProtobuf"); }

        /**
         * Creates a dependency provider for grpcStub (io.grpc:grpc-stub)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getGrpcStub() { return create("grpcStub"); }

        /**
         * Creates a dependency provider for grpcTesting (io.grpc:grpc-testing)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getGrpcTesting() { return create("grpcTesting"); }

        /**
         * Creates a dependency provider for gson (com.google.code.gson:gson)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getGson() { return create("gson"); }

        /**
         * Creates a dependency provider for guava (com.google.guava:guava)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getGuava() { return create("guava"); }

        /**
         * Creates a dependency provider for jcommander (com.beust:jcommander)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getJcommander() { return create("jcommander"); }

        /**
         * Creates a dependency provider for jdom2 (org.jdom:jdom2)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getJdom2() { return create("jdom2"); }

        /**
         * Creates a dependency provider for jqwik (net.jqwik:jqwik)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getJqwik() { return create("jqwik"); }

        /**
         * Creates a dependency provider for jsr305 (com.google.code.findbugs:jsr305)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getJsr305() { return create("jsr305"); }

        /**
         * Creates a dependency provider for junitJupiter (org.junit.jupiter:junit-jupiter)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getJunitJupiter() { return create("junitJupiter"); }

        /**
         * Creates a dependency provider for mockitoCore (org.mockito:mockito-core)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getMockitoCore() { return create("mockitoCore"); }

        /**
         * Creates a dependency provider for protobufBom (com.google.protobuf:protobuf-bom)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getProtobufBom() { return create("protobufBom"); }

        /**
         * Creates a dependency provider for protobufJava (com.google.protobuf:protobuf-java)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getProtobufJava() { return create("protobufJava"); }

        /**
         * Creates a dependency provider for slf4j (org.slf4j:slf4j-api)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getSlf4j() { return create("slf4j"); }

        /**
         * Creates a dependency provider for slf4jJdk14 (org.slf4j:slf4j-jdk14)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getSlf4jJdk14() { return create("slf4jJdk14"); }

        /**
         * Creates a dependency provider for tomcatAnnotationsApi (org.apache.tomcat:annotations-api)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getTomcatAnnotationsApi() { return create("tomcatAnnotationsApi"); }

        /**
         * Creates a dependency provider for truth (com.google.truth:truth)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getTruth() { return create("truth"); }

        /**
         * Creates a dependency provider for truthJava8Extension (com.google.truth.extensions:truth-java8-extension)
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getTruthJava8Extension() { return create("truthJava8Extension"); }

    /**
     * Returns the group of versions at versions
     */
    public VersionAccessors getVersions() { return vaccForVersionAccessors; }

    /**
     * Returns the group of bundles at bundles
     */
    public BundleAccessors getBundles() { return baccForBundleAccessors; }

    /**
     * Returns the group of plugins at plugins
     */
    public PluginAccessors getPlugins() { return paccForPluginAccessors; }

    public static class VersionAccessors extends VersionFactory  {

        public VersionAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

            /**
             * Returns the version associated to this alias: bytes (1.5.0)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getBytes() { return getVersion("bytes"); }

            /**
             * Returns the version associated to this alias: execforkPlugin (0.1.15)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getExecforkPlugin() { return getVersion("execforkPlugin"); }

            /**
             * Returns the version associated to this alias: flogger (0.5.1)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getFlogger() { return getVersion("flogger"); }

            /**
             * Returns the version associated to this alias: grpc (1.42.1)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getGrpc() { return getVersion("grpc"); }

            /**
             * Returns the version associated to this alias: gson (2.8.6)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getGson() { return getVersion("gson"); }

            /**
             * Returns the version associated to this alias: guava (31.0.1-jre)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getGuava() { return getVersion("guava"); }

            /**
             * Returns the version associated to this alias: jcommander (1.81)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getJcommander() { return getVersion("jcommander"); }

            /**
             * Returns the version associated to this alias: jdom2 (2.0.6)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getJdom2() { return getVersion("jdom2"); }

            /**
             * Returns the version associated to this alias: jgoodies (1.6.0)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getJgoodies() { return getVersion("jgoodies"); }

            /**
             * Returns the version associated to this alias: jqwik (1.6.3)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getJqwik() { return getVersion("jqwik"); }

            /**
             * Returns the version associated to this alias: jsr305 (3.0.2)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getJsr305() { return getVersion("jsr305"); }

            /**
             * Returns the version associated to this alias: junitJupiter (5.7.0)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getJunitJupiter() { return getVersion("junitJupiter"); }

            /**
             * Returns the version associated to this alias: mockito (3.9.0)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getMockito() { return getVersion("mockito"); }

            /**
             * Returns the version associated to this alias: owaspDepCheckPlugin (6.5.1)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getOwaspDepCheckPlugin() { return getVersion("owaspDepCheckPlugin"); }

            /**
             * Returns the version associated to this alias: protobuf (3.19.4)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getProtobuf() { return getVersion("protobuf"); }

            /**
             * Returns the version associated to this alias: protobufPlugin (0.8.18)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getProtobufPlugin() { return getVersion("protobufPlugin"); }

            /**
             * Returns the version associated to this alias: slf4j (1.7.32)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getSlf4j() { return getVersion("slf4j"); }

            /**
             * Returns the version associated to this alias: tomcatAnnotationsApi (6.0.53)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getTomcatAnnotationsApi() { return getVersion("tomcatAnnotationsApi"); }

            /**
             * Returns the version associated to this alias: truth (1.1)
             * If the version is a rich version and that its not expressible as a
             * single version string, then an empty string is returned.
             * This version was declared in catalog libs.versions.toml
             */
            public Provider<String> getTruth() { return getVersion("truth"); }

    }

    public static class BundleAccessors extends BundleFactory {

        public BundleAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

    }

    public static class PluginAccessors extends PluginFactory {

        public PluginAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

            /**
             * Creates a plugin provider for execforkPlugin to the plugin id 'com.github.psxpaul.execfork'
             * This plugin was declared in catalog libs.versions.toml
             */
            public Provider<PluginDependency> getExecforkPlugin() { return createPlugin("execforkPlugin"); }

            /**
             * Creates a plugin provider for owaspDepCheckPlugin to the plugin id 'org.owasp.dependencycheck'
             * This plugin was declared in catalog libs.versions.toml
             */
            public Provider<PluginDependency> getOwaspDepCheckPlugin() { return createPlugin("owaspDepCheckPlugin"); }

            /**
             * Creates a plugin provider for protobufPlugin to the plugin id 'com.google.protobuf'
             * This plugin was declared in catalog libs.versions.toml
             */
            public Provider<PluginDependency> getProtobufPlugin() { return createPlugin("protobufPlugin"); }

    }

}
