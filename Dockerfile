FROM borkdude/babashka:0.2.3-SNAPSHOT AS BUILD_CONTAINER
RUN mkdir -p /build/target
ADD src/ /build/src
WORKDIR /build
RUN /usr/local/bin/bb --uberscript target/ld_stats.bb -cp src -m 'ld-stats.core'

FROM borkdude/babashka:0.2.3-SNAPSHOT
COPY --from=BUILD_CONTAINER /build/target/ld_stats.bb /
RUN chmod +x /ld_stats.bb
ENTRYPOINT ["/ld_stats.bb"]