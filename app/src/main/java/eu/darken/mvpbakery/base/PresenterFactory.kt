package eu.darken.mvpbakery.base

interface PresenterFactory<PresenterT : Presenter<*>> {
    fun createPresenter(): FactoryResult<PresenterT>

    class FactoryResult<PresenterT : Presenter<*>> internal constructor(
            internal val presenter: PresenterT?,
            internal val retry: Boolean,
            internal val retryException: Throwable? = null
    ) {
        companion object {

            fun <PresenterT : Presenter<*>> retry(retryException: Throwable? = null): FactoryResult<PresenterT> {
                return FactoryResult(null, true, retryException)
            }

            fun <PresenterT : Presenter<*>> forPresenter(presenter: PresenterT): FactoryResult<PresenterT> {
                return FactoryResult(presenter, false)
            }
        }
    }
}
