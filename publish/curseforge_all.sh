for proj in `cat gameVersions.txt`; do
	./gradlew curseforge -PgameVersion=$proj $*
done
