for proj in `cat gameVersions.txt`; do
	./gradlew publishModrinth -PgameVersion=$proj $*
done
