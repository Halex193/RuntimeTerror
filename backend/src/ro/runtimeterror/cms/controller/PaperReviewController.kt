package ro.runtimeterror.cms.controller

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import ro.runtimeterror.cms.database.tables.ReviewTable
import ro.runtimeterror.cms.model.PaperReview
import ro.runtimeterror.cms.model.Qualifier
import ro.runtimeterror.cms.model.validators.PaperValidator
import ro.runtimeterror.cms.model.validators.UserValidator
import ro.runtimeterror.cms.database.DatabaseSettings.connection
import ro.runtimeterror.cms.database.daos.PaperDAO
import ro.runtimeterror.cms.database.daos.UserDAO
import ro.runtimeterror.cms.exceptions.NoPapersException
import ro.runtimeterror.cms.model.UserReview
import ro.runtimeterror.cms.model.UserType

class PaperReviewController
{

    /**
     * Get all the reviews made by the user or assigned to him and also the other reviews for the same paper
     * If the author is a PC member, he is not allowed to see the other reviews
     */
    fun getReviews(userId: Int): List<PaperReview>
    {
        return getPCMemberReviews(userId)?: throw NoPapersException("The user hasn't made/gotten any reviews")
    }

    private fun getPCMemberReviews(userId: Int): List<PaperReview>? = transaction(connection){
        val authoredPapers: List<Int> = PaperDAO
            .all()
            .filter{ userId in it.authors.map {user-> user.userId } }
            .map { it.paperId }
            .toList()

        return@transaction ReviewTable
            .select{ReviewTable.userID eq userId}
            .filter { it[ReviewTable.paperID] !in authoredPapers }
            .map {
                PaperReview(
                        PaperDAO.findById(it[ReviewTable.paperID])!!,
                        it[ReviewTable.recommandation],
                        Qualifier.from(it[ReviewTable.qualifier]),
                        getOtherReviews(userId, it[ReviewTable.paperID])
                    )
            }
    }

    private fun getOtherReviews(userId: Int, paperID: Int): List<UserReview> = transaction(connection) {
        return@transaction ReviewTable.select {
            (ReviewTable.paperID eq paperID) and (ReviewTable.userID eq userId)
        }.map {
            UserReview(
                    UserDAO.findById(it[ReviewTable.userID])!!,
                    it[ReviewTable.recommandation],
                    Qualifier.from(it[ReviewTable.qualifier])
            )
        }
    }

    private fun checkIfPCMember(userId: Int): Boolean {
        return UserDAO
            .findById(userId)
            ?.type
            ?.value == UserType.PC_MEMBER.value
    }

    /**
     * Change a review
     */
    fun review(userID: Int, paperID: Int, recommendation: String, qualifier: Int)= transaction(connection) {
            UserValidator.exists(userID)
            PaperValidator.exists(paperID)
            ReviewTable
                    .insert {
                        it[ReviewTable.userID] = userID
                        it[ReviewTable.paperID] = paperID
                        it[ReviewTable.qualifier] = Qualifier.from(qualifier).value
                        it[recommandation] = recommendation
                    }
        }
}
