LIBS  += junit
LIBS  += cobertura
LIBS  += pmd
LIBS  += pmd2
LIBS  += jdepend
LIBS  += javancss
LIBS  += checkstyle

FILES += junit-4.8.2.jar
FILES += cobertura-1.9.4.1-bin.tar.bz2
FILES += pmd-src-4.2.5.zip
FILES += pmd-bin-4.2.5.zip
FILES += jdepend-2.9.zip
FILES += javancss-32.53.zip
FILES += checkstyle-5.2-bin.tar.gz

.PHONY: getdeps $(LIBS:%=getdep-%)
getdeps: $(LIBS:%=getdep-%)
$(LIBS:%=getdep-%): getdep-%: $(DESTDIR)/getdep-%.ok

$(DESTDIR)/getdep-junit.ok:      $(DESTDIR)/$(shell cat junit.file)
$(DESTDIR)/getdep-cobertura.ok:  $(DESTDIR)/cobertura-1.9.4.1-bin.tar.bz2
$(DESTDIR)/getdep-pmd.ok:        $(DESTDIR)/pmd-src-4.2.5.zip
$(DESTDIR)/getdep-pmd2.ok:       $(DESTDIR)/pmd-bin-4.2.5.zip
$(DESTDIR)/getdep-jdepend.ok:    $(DESTDIR)/jdepend-2.9.zip
$(DESTDIR)/getdep-javancss.ok:   $(DESTDIR)/javancss-32.53.zip
$(DESTDIR)/getdep-checkstyle.ok: $(DESTDIR)/checkstyle-5.2-bin.tar.gz

$(LIBS:%=$(DESTDIR)/getdep-%.ok): $(DESTDIR)/getdep-%.ok:
	mkdir -p $(dir $@)
	md5sum $< | cut -f 1 -d ' ' > $@.md5.actual
	diff -w $(notdir $<).md5 $@.md5.actual
	touch $@

$(FILES:%=$(DESTDIR)/%): $(DESTDIR)/%: 
	mkdir -p $(dir $@)
	curl `cat $*.url` --location --output $@.tmp
	mv $@.tmp $@

.PHONY: crack $(FILES:%=crack-%)
crack: $(FILES:%=crack-%)
$(FILES:%=crack-%): crack-%: $(DESTDIR)/crack-%.ok
$(FILES:%=$(DESTDIR)/crack-%.ok): $(DESTDIR)/crack-%.ok: $(DESTDIR)/%
	@echo "pattern $*"
	@echo "target  $@"
	false
